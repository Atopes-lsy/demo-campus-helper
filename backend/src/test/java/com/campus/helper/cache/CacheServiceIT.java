package com.campus.helper.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the cache against a real Redis. The behaviours under test — a shared key
 * survived across callers, a TTL actually set — are not something a mocked client can show.
 */
@Testcontainers
class CacheServiceIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {
    };

    static RedissonClient redisson;
    static CacheService cacheService;

    @BeforeAll
    static void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress(
                "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(config);
        cacheService = new CacheService(redisson, new ObjectMapper());
    }

    @AfterAll
    static void tearDown() {
        redisson.shutdown();
    }

    @BeforeEach
    void clearKeys() {
        redisson.getKeys().flushall();
    }

    @Test
    void loadsOnceThenServesFromCache() {
        AtomicInteger loads = new AtomicInteger();

        List<String> first = cacheService.getOrLoad("cache:test:hit", LIST_OF_STRING,
                Duration.ofMinutes(5), Duration.ZERO, () -> {
                    loads.incrementAndGet();
                    return List.of("a", "b");
                });
        List<String> second = cacheService.getOrLoad("cache:test:hit", LIST_OF_STRING,
                Duration.ofMinutes(5), Duration.ZERO, () -> {
                    loads.incrementAndGet();
                    return List.of("a", "b");
                });

        assertThat(first).containsExactly("a", "b");
        assertThat(second).containsExactly("a", "b");
        assertThat(loads.get()).as("第二次应命中缓存，不再回源").isEqualTo(1);
    }

    @Test
    void cachesNegativeResultsSoRepeatedMissesDoNotReachTheSource() {
        AtomicInteger loads = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThat(cacheService.getOrLoad("cache:test:miss", LIST_OF_STRING,
                    Duration.ofMinutes(5), Duration.ZERO, () -> {
                        loads.incrementAndGet();
                        return null;
                    })).isNull();
        }

        assertThat(loads.get())
                .as("空结果也应被缓存（穿透防护），只应回源一次")
                .isEqualTo(1);
    }

    @Test
    void evictForcesTheNextReadToReload() {
        AtomicInteger loads = new AtomicInteger();
        Runnable read = () -> cacheService.getOrLoad("cache:test:evict", LIST_OF_STRING,
                Duration.ofMinutes(5), Duration.ZERO, () -> {
                    loads.incrementAndGet();
                    return List.of("v" + loads.get());
                });

        read.run();
        read.run();
        assertThat(loads.get()).isEqualTo(1);

        cacheService.evict("cache:test:evict");
        read.run();

        assertThat(loads.get()).as("失效后应重新回源").isEqualTo(2);
    }

    @Test
    void jittersTheTtlSoKeysDoNotExpireTogether() {
        Duration ttl = Duration.ofMinutes(5);
        Duration jitter = Duration.ofMinutes(1);

        cacheService.getOrLoad("cache:test:ttl", LIST_OF_STRING, ttl, jitter,
                () -> List.of("x"));

        long ttlMillis = redisson.<String>getBucket("cache:test:ttl").remainTimeToLive();
        assertThat(ttlMillis)
                .as("TTL 应落在 [ttl, ttl+jitter] 区间内（雪崩防护）")
                .isBetween(ttl.toMillis(), ttl.plus(jitter).toMillis());
    }

    @Test
    void concurrentMissesRebuildOnlyOnce() throws Exception {
        int threads = 8;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        cacheService.getOrLoad("cache:test:breakdown", LIST_OF_STRING,
                                Duration.ofMinutes(5), Duration.ZERO, () -> {
                                    loads.incrementAndGet();
                                    sleep(300);   // slow source, so the window is real
                                    return List.of("rebuilt");
                                });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(loads.get())
                .as("击穿防护：并发未命中只应有一个线程回源重建")
                .isEqualTo(1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
