package com.campus.helper.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    /** Stands in for the Redis-backed resolver, so these tests need no container. */
    private final AtomicLong version = new AtomicLong();
    private volatile boolean versionUnavailable = false;

    private final JwtService jwtService = new JwtService(
            new ObjectMapper(),
            (role, userId) -> versionUnavailable ? null : version.get(),
            "test-jwt-secret-that-is-at-least-32-bytes", 12);

    @Test
    void issuedTokenCanBeVerified() {
        JwtService.TokenResult issued = jwtService.issue(42L, "STUDENT", "测试用户", 0L);
        AuthenticatedUser user = jwtService.verify(issued.token());

        assertEquals(42L, user.userId());
        assertEquals("STUDENT", user.role());
        assertEquals("测试用户", user.displayName());
    }

    @Test
    void modifiedTokenIsRejected() {
        String token = jwtService.issue(42L, "STUDENT", "测试用户", 0L).token();
        String modified = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThrows(IllegalArgumentException.class, () -> jwtService.verify(modified));
    }

    @Test
    void tokenIsRejectedAfterItsVersionIsBumped() {
        String token = jwtService.issue(42L, "STUDENT", "测试用户", version.get()).token();
        assertDoesNotThrow(() -> jwtService.verify(token));

        // What a logout or an admin revoke does.
        version.incrementAndGet();

        assertThrows(IllegalArgumentException.class, () -> jwtService.verify(token),
                "版本递增后旧 token 必须失效");
    }

    @Test
    void tokenIssuedAfterABumpStillVerifies() {
        version.incrementAndGet();

        String token = jwtService.issue(42L, "STUDENT", "测试用户", version.get()).token();

        assertDoesNotThrow(() -> jwtService.verify(token));
    }

    @Test
    void versionCheckIsSkippedWhenTheCurrentVersionCannotBeRead() {
        String token = jwtService.issue(42L, "STUDENT", "测试用户", 0L).token();
        versionUnavailable = true;

        // Failing open: a Redis outage must not 401 every request whose token still has a
        // valid signature and has not expired.
        assertDoesNotThrow(() -> jwtService.verify(token));
    }
}
