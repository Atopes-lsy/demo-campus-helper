package com.campus.helper.cache;

import com.campus.helper.event.PricingRulesChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drops cached entries when the data behind them changes.
 *
 * <p>The {@code AFTER_COMMIT} phase is the point of this class: evicting inside the writing
 * transaction would leave the cache empty while the write was still uncommitted, letting a
 * concurrent reader repopulate it from the pre-write state — and then a rollback would
 * leave that stale value cached with nothing to correct it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionListener {

    private final CacheService cacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPricingRulesChanged(PricingRulesChangedEvent event) {
        cacheService.evict(CacheKeys.ACTIVE_PRICING_RULES);
        log.info("[缓存] 定价规则 {} 已变更，派单权重缓存已失效", event.ruleId());
    }
}
