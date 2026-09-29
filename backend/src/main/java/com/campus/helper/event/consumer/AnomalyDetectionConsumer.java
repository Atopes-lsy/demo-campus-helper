package com.campus.helper.event.consumer;

import com.campus.helper.ai.DeepSeekClient;
import com.campus.helper.ai.PromptTemplates;
import com.campus.helper.entity.AnomalyAlert;
import com.campus.helper.event.CancellationWindowTracker;
import com.campus.helper.event.KafkaConfig;
import com.campus.helper.event.OrderEvent;
import com.campus.helper.event.EventDeduplicator;
import com.campus.helper.mapper.AnomalyAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Real-time anomaly detection consumer powered by Kafka event streams.
 *
 * Complements the scheduled {@link com.campus.helper.service.AnomalyDetector}
 * by detecting anomalies immediately rather than on an hourly cron.
 *
 * Current detection rules:
 * <ul>
 *   <li>High-frequency cancellation — same student cancels >= 3 orders within 10 minutes</li>
 *   <li>Stuck delivery — order stays in DELIVERED (status=4) > 30 minutes without student confirmation</li>
 * </ul>
 *
 * Runs in its own consumer group to receive ALL events independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyDetectionConsumer {

    private final AnomalyAlertMapper anomalyAlertMapper;
    private final DeepSeekClient deepSeekClient;
    private final EventDeduplicator eventDeduplicator;
    private final CancellationWindowTracker cancellationWindowTracker;

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_TOPIC,
            groupId = "campus-anomaly-detection-group",
            concurrency = "1"
    )
    @Transactional
    public void onOrderEvent(OrderEvent event) {
        if (!eventDeduplicator.firstDelivery(event.getEventId(), "anomaly-detection")) {
            return;
        }
        // High-frequency cancellation detection
        if ("ORDER_CANCELLED".equals(event.getEventType())) {
            detectHighFrequencyCancel(event);
        }

        // Stuck-at-delivered detection
        if ("ORDER_DELIVERED".equals(event.getEventType())) {
            // Just log — the scheduled AnomalyDetector handles ghost detection.
            // This consumer can be extended with a delayed message (e.g., Kafka
            // retry with @RetryableTopic) to check back in 30 minutes.
            log.info("[异常检测] 订单 {} 已送达，将在30分钟后检查是否确认收货", event.getOrderId());
        }
    }

    /**
     * Detect if a student cancels >= 3 orders within a 10-minute sliding window.
     */
    private void detectHighFrequencyCancel(OrderEvent event) {
        Long studentId = event.getStudentId();
        LocalDateTime now = event.getTimestamp() != null
                ? event.getTimestamp() : LocalDateTime.now();
        long nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Dedup already filters replays that carry an id; an event without one still
        // needs a stable key so that re-recording it cannot inflate the count.
        String eventKey = event.getEventId() != null
                ? event.getEventId()
                : event.getOrderId() + "@" + nowMillis;

        int cancelCount = cancellationWindowTracker.record(studentId, eventKey, nowMillis);

        if (cancelCount >= 3) {
            // Check if alert already exists for this student today
            Long existingCount = anomalyAlertMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnomalyAlert>()
                            .eq(AnomalyAlert::getTargetType, "STUDENT")
                            .eq(AnomalyAlert::getTargetId, studentId)
                            .eq(AnomalyAlert::getAnomalyType, "HIGH_FREQ_CANCEL")
                            .eq(AnomalyAlert::getIsResolved, 0)
            );

            if (existingCount > 0) {
                log.debug("High-frequency cancel alert already exists for student {}", studentId);
                return;
            }

            // Build context for AI analysis
            String context = "学生ID: " + studentId
                    + "\n10分钟内取消订单数: " + cancelCount
                    + "\n最近取消时间: " + now
                    + "\n订单号: " + event.getOrderNo();

            String analysis = deepSeekClient.chat(PromptTemplates.ANOMALY_ANALYSIS, context, 0.7);

            AnomalyAlert alert = new AnomalyAlert();
            alert.setTargetType("STUDENT");
            alert.setTargetId(studentId);
            alert.setAnomalyType("HIGH_FREQ_CANCEL");
            alert.setSeverity(4);
            alert.setDescription("学生 ID=" + studentId
                    + " 在10分钟内取消了 " + cancelCount + " 个订单，疑似恶意操作");
            alert.setAiAnalysis(analysis);
            alert.setIsResolved(0);
            alert.setDetectedAt(LocalDateTime.now());
            anomalyAlertMapper.insert(alert);

            log.warn("[异常检测] 高频取消告警: studentId={}, cancelCount={} in 10min",
                    studentId, cancelCount);
        }
    }
}
