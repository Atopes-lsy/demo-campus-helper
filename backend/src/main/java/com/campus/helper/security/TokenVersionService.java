package com.campus.helper.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Account token versions in Redis.
 *
 * <p>Redis rather than a column on {@code student}/{@code runner}: both tables would need it,
 * the administrator has no row at all, and a bump is a single counter — no schema change or
 * migration to keep the two in step.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenVersionService implements TokenVersionResolver {

    private final RedissonClient redissonClient;

    @Override
    public Long currentVersion(String role, Long userId) {
        try {
            // Must read through RAtomicLong, the same API that writes it. Its increment runs
            // as a Lua INCR, so the stored value is a plain integer — which the default Kryo
            // codec behind RBucket cannot decode (it throws "unregistered class ID"). Mixing
            // the two APIs reads back as a failure, and a failure here means the revocation
            // check is skipped, so the bug shows up as "revocation silently does nothing".
            return redissonClient.getAtomicLong(key(role, userId)).get();
        } catch (Exception e) {
            // Fail open. Rejecting on an unreadable version would turn a Redis blip into a
            // total outage: every request carrying a perfectly valid, unexpired token would
            // 401. Revocation silently stops working until Redis returns, which is the
            // smaller harm here — the affected tokens still expire on their own. A system
            // where instant revocation matters more than availability would fail closed.
            log.warn("[会话] 无法读取 token 版本，本次跳过撤销校验: role={}, userId={}, 原因={}",
                    role, userId, e.getMessage());
            return null;
        }
    }

    /** Invalidate every token issued to this account so far. */
    public void bump(String role, Long userId) {
        RAtomicLong counter = redissonClient.getAtomicLong(key(role, userId));
        long version = counter.incrementAndGet();
        log.info("[会话] 已失效该账号的全部 token: role={}, userId={}, 新版本={}", role, userId, version);
    }

    private String key(String role, Long userId) {
        return "auth:ver:" + role + ":" + userId;
    }
}
