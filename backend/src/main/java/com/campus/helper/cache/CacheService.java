package com.campus.helper.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache-Aside reads on top of Redis, with the three classic failure modes handled
 * explicitly rather than left to chance.
 *
 * <ul>
 *   <li><b>穿透 (penetration)</b> — a lookup for data that does not exist would hit the
 *       source on every call. A miss is cached as {@link #NULL_SENTINEL} for a short TTL
 *       so repeated misses are answered from Redis.</li>
 *   <li><b>击穿 (breakdown)</b> — when a hot key expires, every concurrent caller would
 *       rebuild it at once. A Redisson lock lets one caller rebuild while the rest wait
 *       briefly for its result.</li>
 *   <li><b>雪崩 (avalanche)</b> — keys written with the same TTL expire together. Callers
 *       pass a jitter that is added at random per write, and if Redis is unreachable
 *       altogether the read falls through to the source instead of failing.</li>
 * </ul>
 *
 * <p>Writes are invalidated by eviction rather than update: two concurrent writers can
 * interleave so that the one that wrote the cache last is not the one that wrote the
 * database last, leaving a stale value behind. Deleting cannot go stale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheService {

    /** Stored in place of a value the loader reported as absent. */
    private static final String NULL_SENTINEL = "__NULL__";

    /** Negative results expire quickly so a row created just after the miss is still seen. */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** How long a caller waits for another thread to finish rebuilding before giving up. */
    private static final long REBUILD_WAIT_SECONDS = 2;

    /** Upper bound on holding the rebuild lock, so a crashed holder cannot block readers. */
    private static final long REBUILD_LEASE_SECONDS = 10;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    /**
     * Return the cached value for {@code key}, loading and caching it on a miss.
     *
     * @param type   the cached type, needed because erasure loses the element type of collections
     * @param ttl    base time to live
     * @param jitter random extra time added per write, to spread expirations apart
     * @param loader the source of truth, consulted only on a miss
     */
    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Duration jitter,
                           Supplier<T> loader) {
        try {
            return loadThroughCache(key, type, ttl, jitter, loader);
        } catch (Exception e) {
            // A cache that is down must degrade to "slower", not "broken": read through to
            // the source. This doubles as the avalanche fallback when Redis is unreachable.
            log.warn("[缓存] 不可用，回退到数据源 key={}: {}", key, e.getMessage());
            return loader.get();
        }
    }

    /** Drop a key. Called after the owning write commits, never before. */
    public void evict(String key) {
        try {
            redissonClient.getBucket(key).delete();
            log.debug("[缓存] 已失效 key={}", key);
        } catch (Exception e) {
            // Losing an eviction only costs staleness until the TTL expires, so the write
            // that triggered it must not fail.
            log.warn("[缓存] 失效失败 key={}: {}", key, e.getMessage());
        }
    }

    private <T> T loadThroughCache(String key, TypeReference<T> type, Duration ttl,
                                   Duration jitter, Supplier<T> loader) throws Exception {
        RBucket<String> bucket = redissonClient.getBucket(key);
        String cached = bucket.get();
        if (cached != null) {
            return deserialize(cached, type);
        }

        RLock lock = redissonClient.getLock("cache:rebuild:" + key);
        boolean locked = lock.tryLock(REBUILD_WAIT_SECONDS, REBUILD_LEASE_SECONDS, TimeUnit.SECONDS);
        try {
            if (!locked) {
                // Whoever holds the lock is slower than we are willing to wait. Reading the
                // source spreads the load instead of stacking up waiting threads.
                return loader.get();
            }
            // The previous holder may have populated the key while we waited for the lock.
            String rebuilt = bucket.get();
            if (rebuilt != null) {
                return deserialize(rebuilt, type);
            }
            T value = loader.get();
            bucket.set(value == null ? NULL_SENTINEL : objectMapper.writeValueAsString(value),
                    value == null ? NULL_TTL : jittered(ttl, jitter));
            return value;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private <T> T deserialize(String json, TypeReference<T> type) throws Exception {
        if (NULL_SENTINEL.equals(json)) {
            return null;
        }
        return objectMapper.readValue(json, type);
    }

    private Duration jittered(Duration ttl, Duration jitter) {
        if (jitter == null || jitter.isZero() || jitter.isNegative()) {
            return ttl;
        }
        return ttl.plusMillis(ThreadLocalRandom.current().nextLong(jitter.toMillis() + 1));
    }
}
