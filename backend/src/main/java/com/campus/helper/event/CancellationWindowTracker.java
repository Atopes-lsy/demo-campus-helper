package com.campus.helper.event;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sliding-window counter of a student's cancellations, backed by Redis.
 *
 * <p>Keeping the window in Redis rather than in a local map means it is shared
 * across instances and survives a restart. Members are scored by event timestamp
 * and identified by an event key, so replaying the same event never counts twice.
 */
@Component
@RequiredArgsConstructor
public class CancellationWindowTracker {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final RedissonClient redissonClient;

    /**
     * Record a cancellation and report how many fall inside the trailing window.
     *
     * @param studentId       the student who cancelled
     * @param eventKey        stable identifier of the cancellation event; re-recording
     *                        the same key updates its score instead of adding an entry
     * @param timestampMillis when the cancellation happened, in epoch milliseconds
     * @return number of cancellations in the window, including this one
     */
    public int record(Long studentId, String eventKey, long timestampMillis) {
        RScoredSortedSet<String> window = redissonClient.getScoredSortedSet(key(studentId));
        window.removeRangeByScore(Double.NEGATIVE_INFINITY, true,
                timestampMillis - WINDOW.toMillis(), false);
        window.add(timestampMillis, eventKey);
        // Idle students must not leave keys behind, so the window expires with its content.
        window.expire(WINDOW);
        return window.size();
    }

    private String key(Long studentId) {
        return "anomaly:cancel:" + studentId;
    }
}
