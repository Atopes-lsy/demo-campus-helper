package com.campus.helper.cache;

/**
 * Cache key names.
 *
 * <p>Every key carries the {@value #PREFIX} prefix so cached values are distinguishable
 * from the keys used elsewhere in Redis (the distributed locks and the login-failure
 * counters), and so they can be inspected or flushed as a group.
 */
public final class CacheKeys {

    private static final String PREFIX = "cache:";

    /** Active pricing rules — read on every dispatch, changed rarely. */
    public static final String ACTIVE_PRICING_RULES = PREFIX + "pricing:active";

    /**
     * BI dashboard for a lookback window. Cardinality is bounded by the TTL: each distinct
     * window expires on its own, so a caller walking arbitrary {@code days} values cannot
     * accumulate keys indefinitely.
     */
    public static String biDashboard(int days) {
        return PREFIX + "bi:dashboard:" + days;
    }

    private CacheKeys() {
    }
}
