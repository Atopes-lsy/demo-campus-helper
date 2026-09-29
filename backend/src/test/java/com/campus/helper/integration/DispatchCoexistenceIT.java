package com.campus.helper.integration;

import com.campus.helper.common.BusinessException;
import com.campus.helper.entity.Runner;
import com.campus.helper.event.OutboxDeliveryScheduler;
import com.campus.helper.service.DispatchService;
import com.campus.helper.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Races auto-dispatch against the runner grab path on the same order, against a real MySQL, and
 * asserts exactly one of them can come away owning it.
 *
 * <p>This is the test that makes the "both modes coexist" claim mean something. Dispatch reads a
 * pending snapshot, scores every free runner, and only then writes. A runner can grab that order
 * through {@code OrderService.updateStatus} — a CAS on the same status column — anywhere in
 * between, most dangerously after dispatch's read and before its write. Against the old
 * unconditional {@code updateById}, the grabber's {@code runner_id} was overwritten by whichever
 * runner dispatch had picked, and both paths wrote tracking and marked a runner busy: two owners,
 * and these assertions fail on the busy-runner and tracking counts.
 *
 * <p>The interleaving is biased on purpose — the grabber waits a couple of milliseconds so
 * dispatch gets its read in first — because the whole point is to land inside that window rather
 * than merely run the two calls near each other.
 */
@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=test-dispatch-coexistence-jwt-secret-32b",
        "security.admin.username=admin",
        "security.admin.password=admin123",
        "payment.callback-secret=test-dispatch-coexistence-payment-secret",
        "deepseek.api.key=sk-test-placeholder",
        // No Kafka broker in this test. Keep the poller idle, and replace the delivery scheduler
        // with a mock (below) so the commit-time wake-up cannot block on metadata for a broker
        // that is not there.
        "outbox.publish-interval-ms=600000",
        "dispatch.auto-enabled=false"
})
class DispatchCoexistenceIT {

    private static final int ROUNDS = 30;
    private static final long GRAB_HEAD_START_MS = 2;

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

    /** Stands in for the poller so nothing in this test reaches for Kafka. */
    @MockBean
    OutboxDeliveryScheduler outboxDeliveryScheduler;

    @Autowired
    DispatchService dispatchService;

    @Autowired
    OrderService orderService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void exactlyOnePathClaimsAnyGivenOrder() throws Exception {
        int dispatchWins = 0;
        int grabWins = 0;

        for (int round = 0; round < ROUNDS; round++) {
            // Park every runner from earlier rounds so the only candidates are this round's pair.
            jdbc.update("UPDATE runner SET status = 2");
            long dispatchCandidate = insertRunner();
            long grabber = insertRunner();
            long orderId = insertPendingOrder();

            CyclicBarrier startTogether = new CyclicBarrier(2);
            AtomicReference<Runner> dispatchedTo = new AtomicReference<>();
            AtomicReference<Throwable> dispatchFailure = new AtomicReference<>();
            AtomicBoolean grabSucceeded = new AtomicBoolean(false);
            AtomicReference<Throwable> grabFailure = new AtomicReference<>();

            Thread dispatcher = new Thread(() -> {
                await(startTogether);
                try {
                    dispatchedTo.set(dispatchService.dispatch(orderId));
                } catch (Throwable t) {
                    dispatchFailure.set(t);
                }
            });
            Thread grabberThread = new Thread(() -> {
                await(startTogether);
                sleepQuietly(GRAB_HEAD_START_MS);
                try {
                    orderService.updateStatus(orderId, 1, grabber, "RUNNER");
                    grabSucceeded.set(true);
                } catch (Throwable t) {
                    grabFailure.set(t);
                }
            });

            dispatcher.start();
            grabberThread.start();
            dispatcher.join();
            grabberThread.join();

            Runner winner = dispatchedTo.get();
            boolean dispatchWon = winner != null;

            assertThat(dispatchFailure.get())
                    .as("round %d: 派单不应抛出异常", round)
                    .isNull();
            if (!grabSucceeded.get()) {
                assertThat(grabFailure.get())
                        .as("round %d: 抢单失败必须是 409 状态冲突，而不是别的错误", round)
                        .isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) grabFailure.get()).getCode())
                        .as("round %d: 抢单失败的状态码", round)
                        .isEqualTo(409);
            }

            assertThat(dispatchWon ^ grabSucceeded.get())
                    .as("round %d: 派单与抢单必须恰好一方成功 (派单=%s, 抢单=%s, 抢单异常=%s)",
                            round, winner, grabSucceeded.get(), grabFailure.get())
                    .isTrue();

            long expectedOwner = dispatchWon ? winner.getId() : grabber;
            assertThat(runnerIdOf(orderId))
                    .as("round %d: 订单必须归胜方所有，不能被败方覆盖", round)
                    .isEqualTo(expectedOwner);

            assertThat(busyRunnerCount(dispatchCandidate, grabber))
                    .as("round %d: 恰好一名跑腿员被置为接单中", round)
                    .isEqualTo(1);

            assertThat(claimsRecorded(orderId))
                    .as("round %d: 0→1 的轨迹只能有一条，败方不得写入", round)
                    .isEqualTo(1);

            if (dispatchWon) {
                dispatchWins++;
            } else {
                grabWins++;
            }
        }

        // The grabber's head start is deliberate, so it is expected to win every round: dispatch
        // spends a Redis round trip plus four queries before it writes, far more than the two
        // milliseconds the grabber waits. What matters is that the grabber really did win some
        // rounds — that is the interleaving where it claims the order between dispatch's read and
        // dispatch's write, which is the window this test exists to cover. Were grabWins zero every
        // round would have been the safe ordering and the run would prove nothing.
        assertThat(grabWins)
                .as("抢单获胜轮数（必须命中「派单读完之后、写入之前」的窗口）")
                .isGreaterThan(0);
        assertThat(dispatchWins + grabWins).isEqualTo(ROUNDS);
    }

    /**
     * The other half of the picture, and the one the raced test above cannot see: it only ever
     * exercises the losing branch of the CAS. If {@code updateStatusIfCurrent} returned 0 on a real
     * MySQL for every dispatch — a wrong parameter binding would do exactly that — the race test
     * would still pass while auto-dispatch silently assigned nothing. This asserts the claim
     * actually lands.
     */
    @Test
    void dispatchClaimsAPendingOrderWhenNothingRacesIt() {
        jdbc.update("UPDATE runner SET status = 2");
        long runner = insertRunner();
        long orderId = insertPendingOrder();

        Runner assigned = dispatchService.dispatch(orderId);

        assertThat(assigned)
                .as("无人竞争时派单必须成功")
                .isNotNull();
        assertThat(runnerIdOf(orderId))
                .as("订单必须落到派单选中的跑腿员名下")
                .isEqualTo(assigned.getId());
        assertThat(statusOf(orderId))
                .as("订单必须变为已派单")
                .isEqualTo(1);
        assertThat(busyRunnerCount(runner))
                .as("被选中的跑腿员必须置为接单中")
                .isEqualTo(1);
        assertThat(claimsRecorded(orderId))
                .as("必须写入一条 0→1 的轨迹")
                .isEqualTo(1);
    }

    /**
     * Verifies the commit-time wake-up actually fires through Spring's transaction event
     * machinery — the mechanism that takes auto-dispatch from "up to a poll interval late" to
     * immediate. The poll interval is 600000ms in this test, so a delivery pass arriving here
     * cannot have come from the poller.
     *
     * <p>The spy is a mock, so the pass itself does nothing and nothing reaches for a Kafka broker.
     */
    @Test
    void committingAnOutboxWriteWakesDeliveryWithoutWaitingForThePoll() {
        // The context is cached across methods and this bean is shared, so start from a clean slate.
        clearInvocations(outboxDeliveryScheduler);
        jdbc.update("UPDATE runner SET status = 2");
        insertRunner();
        long orderId = insertPendingOrder();

        // Dispatch writes ORDER_DISPATCHED into the outbox inside its own transaction.
        assertThat(dispatchService.dispatch(orderId)).isNotNull();

        verify(outboxDeliveryScheduler, timeout(5000)).deliver();
    }

    /**
     * runner_no is VARCHAR(20) and order_no VARCHAR(30), so keep these short and unique. Static
     * because JUnit builds a fresh instance per test method while the MySQL container is shared, so
     * a per-instance counter would restart at RIT1 and collide on the unique key.
     */
    private static int sequence = 0;

    private long insertRunner() {
        jdbc.update("INSERT INTO runner"
                        + "(runner_no, name, phone, id_card, status, rating, max_concurrent)"
                        + " VALUES(?,?,?,?,0,5.0,3)",
                "RIT" + (++sequence), "IT跑腿员", "13800000000", "110101199001010011");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPendingOrder() {
        jdbc.update("INSERT INTO order_info"
                        + "(order_no, student_id, order_type, title, pickup_location,"
                        + " delivery_location, amount, tip, status)"
                        + " VALUES(?,?,?,?,?,?,?,?,0)",
                "OIT" + (++sequence), 1L, 2, "IT待派单订单", "取件点", "送达点", 10.00, 0.00);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long runnerIdOf(long orderId) {
        return jdbc.queryForObject("SELECT runner_id FROM order_info WHERE id=?", Long.class, orderId);
    }

    private Integer statusOf(long orderId) {
        return jdbc.queryForObject("SELECT status FROM order_info WHERE id=?", Integer.class, orderId);
    }

    private int busyRunnerCount(long... runnerIds) {
        int busy = 0;
        for (long runnerId : runnerIds) {
            Integer status = jdbc.queryForObject(
                    "SELECT status FROM runner WHERE id=?", Integer.class, runnerId);
            if (status != null && status == 1) {
                busy++;
            }
        }
        return busy;
    }

    private int claimsRecorded(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_tracking"
                + " WHERE order_id=? AND status_from=0 AND status_to=1", Integer.class, orderId);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
