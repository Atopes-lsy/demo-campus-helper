package com.campus.helper.service;

import com.campus.helper.entity.Order;
import com.campus.helper.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

/**
 * Pins the transaction boundary on {@link OrderTimeoutService}.
 *
 * <p>These handlers used to be private methods on the scheduler, called through {@code this},
 * which bypassed the Spring proxy and made {@code @Transactional} a no-op. Every statement
 * then committed on its own, so a failure part-way through left an order auto-confirmed with
 * its payment unsettled. This test fails against that arrangement and passes once the calls
 * cross a bean boundary.
 *
 * <p>The test method is deliberately not {@code @Transactional} — an ambient test transaction
 * would roll everything back itself and the assertions below would hold for the wrong reason.
 */
@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=test-order-timeout-jwt-secret-at-least-32-bytes",
        "security.admin.username=admin",
        "security.admin.password=admin123",
        "payment.callback-secret=test-order-timeout-payment-secret",
        "deepseek.api.key=sk-test-placeholder",
        // Keep the outbox poller from reaching for a Kafka broker this test does not start.
        "outbox.publish-interval-ms=600000"
})
class OrderTimeoutServiceIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("campus_helper")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @SpyBean
    NotificationService notificationService;

    @Autowired
    OrderTimeoutService orderTimeoutService;

    @Autowired
    OrderMapper orderMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rollsBackOrderSettlementAndTrackingWhenNotificationFails() {
        long orderId = insertDeliveredOrder();
        insertPaidPayment(orderId);

        doThrow(new IllegalStateException("notification unavailable"))
                .when(notificationService).sendStatusChange(any(Order.class), anyInt());

        Order order = orderMapper.selectById(orderId);
        assertThatThrownBy(() -> orderTimeoutService.autoConfirmOrder(order))
                .isInstanceOf(IllegalStateException.class);

        assertThat(statusOf(orderId))
                .as("订单状态变更必须随事务回滚")
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT pay_status FROM payment WHERE order_id=?", Integer.class, orderId))
                .as("支付结算必须随事务回滚")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_tracking WHERE order_id=?", Integer.class, orderId))
                .as("轨迹记录必须随事务回滚")
                .isZero();
    }

    private Integer statusOf(long orderId) {
        return jdbc.queryForObject("SELECT status FROM order_info WHERE id=?", Integer.class, orderId);
    }

    private long insertDeliveredOrder() {
        jdbc.update("INSERT INTO order_info"
                        + "(order_no, student_id, order_type, title, pickup_location, delivery_location,"
                        + " amount, tip, status, created_at, updated_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,4,NOW(),NOW())",
                "ORD-IT-" + System.nanoTime(), 1L, 2, "IT订单", "取件点", "送达点", 10.00, 0.00);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertPaidPayment(long orderId) {
        jdbc.update("INSERT INTO payment"
                        + "(payment_no, order_id, payer_id, payee_id, amount, pay_status, created_at)"
                        + " VALUES(?,?,?,?,?,2,NOW())",
                "PAY-IT-" + orderId, orderId, 1L, 2L, 10.00);
    }
}
