package com.campus.helper.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.entity.Order;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scans for timed-out orders every minute and hands each one to
 * {@link OrderTimeoutService}, which owns the transactional work.
 *
 * <p>Runs on every instance that has the application context — there is no
 * distributed lock, so scaling the backend beyond one replica makes each order
 * eligible for cancellation (and refund) more than once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutScheduler {

    private final OrderMapper orderMapper;
    private final OrderTimeoutService orderTimeoutService;

    @Scheduled(cron = "0 * * * * ?")
    public void handleTimeoutOrders() {
        List<Order> pending = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getStatus, 0)
            .lt(Order::getCreatedAt, LocalDateTime.now().minusMinutes(30)));

        for (Order order : pending) {
            try { orderTimeoutService.cancelTimeoutOrder(order); }
            catch (Exception e) { log.error("超时取消失败: orderId={}", order.getId(), e); }
        }

        List<Order> delivered = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getStatus, 4)
            .lt(Order::getUpdatedAt, LocalDateTime.now().minusHours(2)));

        for (Order order : delivered) {
            try { orderTimeoutService.autoConfirmOrder(order); }
            catch (Exception e) { log.error("自动确认失败: orderId={}", order.getId(), e); }
        }
    }
}
