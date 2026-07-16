package com.campus.helper.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.common.StatusFlowValidator;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.OrderTrackingMapper;
import com.campus.helper.service.NotificationService;
import com.campus.helper.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutScheduler {

    private final OrderMapper orderMapper;
    private final OrderTrackingMapper trackingMapper;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 * * * * ?")
    public void handleTimeoutOrders() {
        List<Order> pending = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getStatus, 0)
            .lt(Order::getCreatedAt, LocalDateTime.now().minusMinutes(30)));

        for (Order order : pending) {
            try { cancelTimeoutOrder(order); }
            catch (Exception e) { log.error("超时取消失败: orderId={}", order.getId(), e); }
        }

        List<Order> delivered = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getStatus, 4)
            .lt(Order::getUpdatedAt, LocalDateTime.now().minusHours(2)));

        for (Order order : delivered) {
            try { autoConfirmOrder(order); }
            catch (Exception e) { log.error("自动确认失败: orderId={}", order.getId(), e); }
        }
    }

    @Transactional
    private void cancelTimeoutOrder(Order order) {
        order.setStatus(6);
        orderMapper.updateById(order);

        trackingMapper.insert(buildTracking(order.getId(), 0, 6, "系统自动取消：超30分钟无人接单", null, "SYSTEM"));

        if (order.getTip().compareTo(BigDecimal.ZERO) > 0) {
            paymentService.autoRefund(order.getId(), "订单超时自动取消");
        }
        notificationService.sendStatusChange(order, 6);
        log.info("超时取消: orderNo={}", order.getOrderNo());
    }

    @Transactional
    private void autoConfirmOrder(Order order) {
        Integer oldStatus = order.getStatus();
        order.setStatus(5);
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        trackingMapper.insert(buildTracking(order.getId(), oldStatus, 5, "系统自动确认收货：超2小时未确认", null, "SYSTEM"));

        paymentService.autoSettle(order.getId());
        notificationService.sendStatusChange(order, 5);
        log.info("自动确认: orderNo={}", order.getOrderNo());
    }

    private OrderTracking buildTracking(Long orderId, Integer from, Integer to, String desc, Long operatorId, String operatorType) {
        OrderTracking t = new OrderTracking();
        t.setOrderId(orderId);
        t.setStatusFrom(from);
        t.setStatusTo(to);
        t.setDescription(desc);
        t.setOperatorId(operatorId);
        t.setOperatorType(operatorType);
        return t;
    }
}
