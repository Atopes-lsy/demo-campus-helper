package com.campus.helper.service;

import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.OrderTrackingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Timeout handling for orders, driven by
 * {@link com.campus.helper.scheduler.OrderTimeoutScheduler}.
 *
 * <p>These methods live in their own bean on purpose: {@code @Transactional} is applied by
 * the Spring proxy, so the scheduler has to call them across a bean boundary. Keeping them
 * as private methods invoked through {@code this} would bypass the proxy and silently run
 * every statement in its own transaction — leaving an order cancelled even when the refund
 * that followed it failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    private final OrderMapper orderMapper;
    private final OrderTrackingMapper trackingMapper;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    /**
     * Cancel an order that nobody claimed within the pending window (status 0 -> 6),
     * refunding any tip that was paid.
     */
    @Transactional
    public void cancelTimeoutOrder(Order order) {
        Integer oldStatus = order.getStatus();
        order.setStatus(6);
        orderMapper.updateById(order);

        trackingMapper.insert(buildTracking(order.getId(), oldStatus, 6,
                "系统自动取消：超30分钟无人接单", "SYSTEM"));

        if (order.getTip() != null && order.getTip().compareTo(BigDecimal.ZERO) > 0) {
            paymentService.autoRefund(order.getId(), "订单超时自动取消");
        }
        notificationService.sendStatusChange(order, 6);
        log.info("超时取消: orderNo={}", order.getOrderNo());
    }

    /**
     * Confirm an order the student left unconfirmed past the delivered window (status 4 -> 5),
     * settling the payment.
     */
    @Transactional
    public void autoConfirmOrder(Order order) {
        Integer oldStatus = order.getStatus();
        order.setStatus(5);
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        trackingMapper.insert(buildTracking(order.getId(), oldStatus, 5,
                "系统自动确认收货：超2小时未确认", "SYSTEM"));

        paymentService.autoSettle(order.getId());
        notificationService.sendStatusChange(order, 5);
        log.info("自动确认: orderNo={}", order.getOrderNo());
    }

    private OrderTracking buildTracking(Long orderId, Integer from, Integer to,
                                        String desc, String operatorType) {
        OrderTracking t = new OrderTracking();
        t.setOrderId(orderId);
        t.setStatusFrom(from);
        t.setStatusTo(to);
        t.setDescription(desc);
        t.setOperatorId(null);
        t.setOperatorType(operatorType);
        return t;
    }
}
