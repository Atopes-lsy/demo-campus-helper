package com.campus.helper.event;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the sliding window against a real Redis, since the purge threshold and the
 * idempotent member key are the whole point of moving it off the heap.
 */
@Testcontainers
class CancellationWindowTrackerIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static RedissonClient redisson;
    static CancellationWindowTracker tracker;

    @BeforeAll
    static void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress(
                "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(config);
        tracker = new CancellationWindowTracker(redisson);
    }

    @AfterAll
    static void tearDown() {
        redisson.shutdown();
    }

    @Test
    void countsEveryCancellationInsideTheWindow() {
        long now = Instant.now().toEpochMilli();

        assertThat(tracker.record(1L, "e1", now)).isEqualTo(1);
        assertThat(tracker.record(1L, "e2", now + 1_000)).isEqualTo(2);
        assertThat(tracker.record(1L, "e3", now + 2_000)).isEqualTo(3);
    }

    @Test
    void dropsCancellationsOlderThanTenMinutes() {
        long now = Instant.now().toEpochMilli();

        tracker.record(2L, "old-1", now - Duration.ofMinutes(11).toMillis());
        tracker.record(2L, "old-2", now - Duration.ofMinutes(10).toMillis() - 1);

        assertThat(tracker.record(2L, "fresh", now))
                .as("十分钟前的取消不应计入窗口")
                .isEqualTo(1);
    }

    @Test
    void recordingTheSameEventTwiceCountsOnce() {
        long now = Instant.now().toEpochMilli();

        assertThat(tracker.record(3L, "replayed", now)).isEqualTo(1);
        assertThat(tracker.record(3L, "replayed", now + 500))
                .as("同一事件重复投递不应重复计数")
                .isEqualTo(1);
    }

    @Test
    void windowExpiresOnceTheStudentStopsCancelling() {
        long now = Instant.now().toEpochMilli();
        tracker.record(4L, "e1", now);

        assertThat(redisson.getScoredSortedSet("anomaly:cancel:4").remainTimeToLive())
                .as("空闲学生的窗口键应带 TTL，不能在 Redis 里常驻")
                .isPositive();
    }
}
