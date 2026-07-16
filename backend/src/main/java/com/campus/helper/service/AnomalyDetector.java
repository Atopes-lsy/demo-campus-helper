package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.ai.DeepSeekClient;
import com.campus.helper.ai.PromptTemplates;
import com.campus.helper.entity.AnomalyAlert;
import com.campus.helper.entity.Order;
import com.campus.helper.mapper.AnomalyAlertMapper;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.RunnerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetector {

    private final OrderMapper orderMapper;
    private final RunnerMapper runnerMapper;
    private final AnomalyAlertMapper anomalyAlertMapper;
    private final DeepSeekClient deepSeekClient;

    /**
     * Scheduled anomaly scan - runs at the top of every hour.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scanAll() {
        log.info("Starting scheduled anomaly scan...");
        detectGhostRunners();
        detectHighCancelRate();
        log.info("异常扫描完成");
    }

    /**
     * Detect ghost runners: orders that have been in active status (1,2,3)
     * for over 2 hours without progress, potentially indicating an unresponsive runner.
     */
    void detectGhostRunners() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        List<Order> stuckOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus, List.of(1, 2, 3))
                        .lt(Order::getUpdatedAt, cutoff)
        );

        for (Order order : stuckOrders) {
            // Skip if alert already exists for this order
            Long existingCount = anomalyAlertMapper.selectCount(
                    new LambdaQueryWrapper<AnomalyAlert>()
                            .eq(AnomalyAlert::getTargetType, "ORDER")
                            .eq(AnomalyAlert::getTargetId, order.getId())
                            .eq(AnomalyAlert::getAnomalyType, "GHOST_RUNNER")
                            .eq(AnomalyAlert::getIsResolved, 0)
            );

            if (existingCount > 0) {
                log.debug("Ghost runner alert already exists for order {}", order.getId());
                continue;
            }

            // Build analysis context for AI
            String context = "订单ID: " + order.getId()
                    + "\n订单号: " + order.getOrderNo()
                    + "\n当前状态: " + order.getStatus()
                    + "\n跑腿员ID: " + order.getRunnerId()
                    + "\n最后更新时间: " + order.getUpdatedAt()
                    + "\n已停滞超过2小时";

            String analysis = deepSeekClient.chat(PromptTemplates.ANOMALY_ANALYSIS, context, 0.7);

            // Save anomaly alert
            AnomalyAlert alert = new AnomalyAlert();
            alert.setTargetType("ORDER");
            alert.setTargetId(order.getId());
            alert.setAnomalyType("GHOST_RUNNER");
            alert.setSeverity(3);
            alert.setDescription("订单 " + order.getOrderNo() + " 长时间处于活跃状态（超过2小时未更新），疑似幽灵跑腿员");
            alert.setAiAnalysis(analysis);
            alert.setIsResolved(0);
            alert.setDetectedAt(LocalDateTime.now());
            anomalyAlertMapper.insert(alert);

            log.warn("Ghost runner detected: orderId={}, runnerId={}, status={}, lastUpdated={}",
                    order.getId(), order.getRunnerId(), order.getStatus(), order.getUpdatedAt());
        }

        if (!stuckOrders.isEmpty()) {
            log.info("Ghost runner scan complete: {} stuck orders detected", stuckOrders.size());
        }
    }

    /**
     * Detect students with excessive order cancellation rates (over 30% in the past 30 days,
     * minimum 5 orders). Generates anomaly alerts for flagged students.
     */
    void detectHighCancelRate() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Map<String, Object>> stats = orderMapper.selectStudentCancelStats(since);

        for (Map<String, Object> stat : stats) {
            Long studentId = (Long) stat.get("student_id");
            Long totalOrders = ((Number) stat.get("total_orders")).longValue();
            Long canceledOrders = ((Number) stat.get("canceled_orders")).longValue();

            // Already filtered by SQL HAVING total_orders >= 5, but double-check
            if (totalOrders < 5) {
                continue;
            }

            double cancelRate = (double) canceledOrders / totalOrders;
            if (cancelRate <= 0.30) {
                continue;
            }

            // Skip if alert already exists for this student
            Long existingCount = anomalyAlertMapper.selectCount(
                    new LambdaQueryWrapper<AnomalyAlert>()
                            .eq(AnomalyAlert::getTargetType, "STUDENT")
                            .eq(AnomalyAlert::getTargetId, studentId)
                            .eq(AnomalyAlert::getAnomalyType, "HIGH_CANCEL_RATE")
                            .eq(AnomalyAlert::getIsResolved, 0)
            );

            if (existingCount > 0) {
                log.debug("High cancel rate alert already exists for student {}", studentId);
                continue;
            }

            // Build analysis context for AI
            String cancelPercent = String.format("%.1f%%", cancelRate * 100);
            String context = "学生ID: " + studentId
                    + "\n近30天订单总数: " + totalOrders
                    + "\n取消订单数: " + canceledOrders
                    + "\n取消率: " + cancelPercent;

            String analysis = deepSeekClient.chat(PromptTemplates.ANOMALY_ANALYSIS, context, 0.7);

            // Save anomaly alert
            AnomalyAlert alert = new AnomalyAlert();
            alert.setTargetType("STUDENT");
            alert.setTargetId(studentId);
            alert.setAnomalyType("HIGH_CANCEL_RATE");
            alert.setSeverity(3);
            alert.setDescription("学生 ID=" + studentId + " 近30天取消率 " + cancelPercent
                    + "（" + canceledOrders + "/" + totalOrders + "），超过30%阈值");
            alert.setAiAnalysis(analysis);
            alert.setIsResolved(0);
            alert.setDetectedAt(LocalDateTime.now());
            anomalyAlertMapper.insert(alert);

            log.warn("High cancel rate detected: studentId={}, cancelRate={} ({} / {})",
                    studentId, cancelPercent, canceledOrders, totalOrders);
        }

        log.info("High cancel rate scan complete: {} student stats evaluated", stats.size());
    }
}
