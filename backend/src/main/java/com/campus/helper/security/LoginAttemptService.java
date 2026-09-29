package com.campus.helper.security;

import com.campus.helper.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Throttles repeated failed logins per account, backed by Redis so the count is shared
 * across instances and survives a restart.
 *
 * <p>The window is fixed, not sliding: the TTL is set when the first failure is recorded and
 * is not extended by later ones. A sliding window would let an attacker hold a victim's
 * account locked indefinitely by dripping one attempt in before each expiry.
 *
 * <p>Note the trade-off inherent to locking by account: an attacker who knows a student
 * number can lock that student out for the window without ever guessing the password.
 * That is why the window is short and the threshold low — this raises the cost of guessing
 * without making the account permanently unusable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final RedissonClient redissonClient;

    /**
     * Reject the attempt if the account has already exhausted its failures.
     *
     * @param scope      the account type, so the same identifier on two types does not collide
     * @param identifier student number, phone, or admin username
     */
    public void assertNotLocked(String scope, String identifier) {
        RAtomicLong counter = counter(scope, identifier);
        long failures = counter.get();
        if (failures >= MAX_ATTEMPTS) {
            long remainingMs = counter.remainTimeToLive();
            long remainingMinutes = remainingMs > 0 ? remainingMs / 60_000 + 1 : 1;
            log.warn("[登录锁定] scope={}, identifier={}, 失败次数={}", scope, identifier, failures);
            throw new BusinessException(429, "登录失败次数过多，请 " + remainingMinutes + " 分钟后重试");
        }
    }

    /**
     * Record a failed attempt, starting the window on the first one.
     *
     * <p>The TTL is applied with {@code expireIfNotSet} on every attempt rather than only when
     * the count reaches 1. Two things follow, and both matter:
     *
     * <ul>
     *   <li><b>Fixed window.</b> An existing TTL is left alone, so the window always starts at
     *       the first failure. Re-applying it unconditionally would make the window slide, and
     *       an attacker could then hold a victim's account locked indefinitely by submitting
     *       one attempt just before each expiry.</li>
     *   <li><b>Self-repair.</b> INCR and EXPIRE are separate commands, so a crash between them
     *       can leave a counter with no expiry — and a counter that never expires locks the
     *       account permanently once it crosses the threshold. Because the check runs on every
     *       attempt, the very next failure repairs it. Atomicity is not needed when the second
     *       step is idempotent and retried on every call.</li>
     * </ul>
     */
    public void recordFailure(String scope, String identifier) {
        RAtomicLong counter = counter(scope, identifier);
        counter.incrementAndGet();
        counter.expireIfNotSet(WINDOW);
    }

    /** Clear the counter after a successful login. */
    public void reset(String scope, String identifier) {
        counter(scope, identifier).delete();
    }

    private RAtomicLong counter(String scope, String identifier) {
        return redissonClient.getAtomicLong("auth:fail:" + scope + ":" + identifier);
    }
}
