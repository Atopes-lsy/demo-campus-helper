package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.cache.CacheKeys;
import com.campus.helper.cache.CacheService;
import com.campus.helper.common.BusinessException;
import com.campus.helper.entity.PricingRule;
import com.campus.helper.event.PricingRulesChangedEvent;
import com.campus.helper.mapper.PricingRuleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Reads and writes the pricing rules that drive dispatch scoring.
 *
 * <p>These rows are read on every dispatch but change only when an administrator tunes
 * them, which is the classic read-mostly shape worth caching. The cached value is dropped
 * after a write commits, so dispatch never scores against a rule set that has been changed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingRuleService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    /** Spreads expirations so the rule cache does not expire in lockstep with anything else. */
    private static final Duration CACHE_JITTER = Duration.ofMinutes(1);

    private final PricingRuleMapper pricingRuleMapper;
    private final CacheService cacheService;
    private final ApplicationEventPublisher eventPublisher;

    /** Active rules, served from cache when warm. */
    public List<PricingRule> activeRules() {
        return cacheService.getOrLoad(CacheKeys.ACTIVE_PRICING_RULES,
                new TypeReference<List<PricingRule>>() {
                },
                CACHE_TTL, CACHE_JITTER,
                () -> pricingRuleMapper.selectList(
                        new LambdaQueryWrapper<PricingRule>().eq(PricingRule::getIsActive, 1)));
    }

    /**
     * Apply an administrator's change to one rule and drop the cached rule set once the
     * write has committed.
     *
     * @param priceFactor new factor, or null to leave it unchanged
     * @param isActive    new activation state, or null to leave it unchanged
     * @param reasoning   note to store alongside the rule (for example, the AI's rationale)
     */
    @Transactional
    public PricingRule updateRule(Long id, BigDecimal priceFactor, Boolean isActive, String reasoning) {
        PricingRule rule = pricingRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "定价规则不存在");
        }
        if (priceFactor == null && isActive == null && reasoning == null) {
            throw new BusinessException(400, "没有需要更新的字段");
        }
        if (priceFactor != null) {
            if (priceFactor.signum() <= 0) {
                throw new BusinessException(400, "定价系数必须大于 0");
            }
            rule.setPriceFactor(priceFactor);
        }
        if (isActive != null) {
            rule.setIsActive(isActive ? 1 : 0);
        }
        if (reasoning != null) {
            rule.setAiReasoning(reasoning);
        }
        pricingRuleMapper.updateById(rule);

        // Consumed by CacheEvictionListener once this transaction commits.
        eventPublisher.publishEvent(new PricingRulesChangedEvent(id));
        log.info("[定价规则] 已更新 id={}, priceFactor={}, isActive={}", id, priceFactor, isActive);
        return rule;
    }
}
