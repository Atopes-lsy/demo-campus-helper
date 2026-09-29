package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.entity.Payment;
import com.campus.helper.entity.Order;
import com.campus.helper.mapper.PaymentMapper;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;
    private final RedissonClient redissonClient;

    /**
     * Create a Payment entity for the given order and pay channel.
     * Auto-generates paymentNo as "PAY" + yyyyMMddHHmmss + 4-digit random.
     * Initial status is 0 (pending).
     */
    @Transactional
    public Payment createPayment(Long orderId, String payChannel, Long payerId) {
        if (orderId == null || payChannel == null || payChannel.isBlank()) {
            throw new BusinessException(400, "orderId和payChannel不能为空");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!payerId.equals(order.getStudentId())) {
            throw new BusinessException(403, "只能为自己的订单创建支付");
        }
        Payment existing = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId).last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        Payment payment = new Payment();
        String paymentNo = "PAY"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        payment.setPaymentNo(paymentNo);
        payment.setOrderId(orderId);
        payment.setPayerId(payerId);
        payment.setPayeeId(order.getRunnerId() == null ? 0L : order.getRunnerId());
        payment.setAmount(order.getAmount().add(order.getTip() == null
                ? java.math.BigDecimal.ZERO : order.getTip()));
        payment.setPayChannel(payChannel);
        payment.setPayStatus(0);
        payment.setCreatedAt(LocalDateTime.now());
        paymentMapper.insert(payment);
        log.info("Payment created: paymentNo={}, orderId={}", paymentNo, orderId);
        return payment;
    }

    /**
     * Handle payment gateway callback with distributed lock to prevent duplicate processing.
     * On success: status becomes 2 (paid), sets paidAt.
     * On failure: status reverts to 0 (pending).
     */
    @Transactional
    public void handleCallback(String paymentNo, String transactionId, boolean success) {
        RLock lock = redissonClient.getLock("pay:callback:" + paymentNo);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(409, "支付回调处理中，请稍后重试");
            }

            Payment payment = paymentMapper.selectOne(
                    new LambdaQueryWrapper<Payment>()
                            .eq(Payment::getPaymentNo, paymentNo)
            );
            if (payment == null) {
                throw new BusinessException(404, "支付记录不存在");
            }

            if (payment.getPayStatus() != null && payment.getPayStatus() >= 2) {
                log.info("Duplicate callback ignored for payment {}", paymentNo);
                return;
            }
            payment.setTransactionId(transactionId);
            if (success) {
                payment.setPayStatus(2);
                payment.setPaidAt(LocalDateTime.now());
            } else {
                payment.setPayStatus(0);
            }
            paymentMapper.updateById(payment);
            notificationService.sendPaymentNotification(payment, success ? "成功" : "失败");

            log.info("Payment callback handled: paymentNo={}, success={}", paymentNo, success);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "支付回调处理被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Settle a paid payment: status 2 (paid) -> 3 (settled).
     */
    @Transactional
    public void autoSettle(Long orderId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getPayStatus, 2)
        );
        if (payment == null) {
            log.warn("No paid payment found for settlement: orderId={}", orderId);
            return;
        }
        payment.setPayStatus(3);
        payment.setSettledAt(LocalDateTime.now());
        paymentMapper.updateById(payment);
        log.info("Payment settled: orderId={}, paymentNo={}", orderId, payment.getPaymentNo());
    }

    /**
     * Refund a paid payment: status 2 (paid) -> 4 (refunded).
     */
    @Transactional
    public void autoRefund(Long orderId, String reason) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getPayStatus, 2)
        );
        if (payment == null) {
            log.warn("No paid payment found for refund: orderId={}", orderId);
            return;
        }
        payment.setPayStatus(4);
        paymentMapper.updateById(payment);
        log.info("Payment refunded: orderId={}, paymentNo={}, reason={}",
                orderId, payment.getPaymentNo(), reason);
    }
}
