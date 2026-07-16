package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.ai.DeepSeekClient;
import com.campus.helper.ai.PromptTemplates;
import com.campus.helper.dto.PriceEstimate;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.PricingRule;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.PricingRuleMapper;
import com.campus.helper.mapper.RunnerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartPricingService {

    private final PricingRuleMapper pricingRuleMapper;
    private final DeepSeekClient deepSeekClient;
    private final OrderMapper orderMapper;
    private final RunnerMapper runnerMapper;

    /**
     * Estimate the delivery price based on distance and time slot, applying
     * all active pricing rules.
     *
     * @param distanceMeters estimated delivery distance in meters
     * @param timeSlot       time slot identifier (e.g. 0=morning, 1=lunch, 2=afternoon, etc.)
     * @return a PriceEstimate with base price, applied factors, and final estimated price
     */
    public PriceEstimate estimate(double distanceMeters, int timeSlot) {
        // Base price: ¥3.0 for first 500m, +¥2.0 per additional 500m
        double base = 3.0 + Math.max(0.0, (distanceMeters - 500) / 500) * 2.0;
        BigDecimal basePrice = BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP);

        // Load active pricing rules
        List<PricingRule> activeRules = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>().eq(PricingRule::getIsActive, 1)
        );

        // Build factors map and compute product
        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        factors.put("base", basePrice);
        BigDecimal product = BigDecimal.ONE;

        for (PricingRule rule : activeRules) {
            if (rule.getPriceFactor() == null) {
                continue;
            }

            if ("TIME".equals(rule.getConditionType())) {
                // TIME rules: apply only if conditionExpr matches the given timeSlot
                try {
                    int ruleSlot = Integer.parseInt(rule.getConditionExpr());
                    if (ruleSlot == timeSlot) {
                        factors.put(rule.getName(), rule.getPriceFactor());
                        product = product.multiply(rule.getPriceFactor());
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid conditionExpr '{}' for TIME pricing rule '{}'",
                            rule.getConditionExpr(), rule.getName());
                }
            } else if ("GLOBAL".equals(rule.getConditionType())) {
                // GLOBAL rules: apply to all orders
                factors.put(rule.getName(), rule.getPriceFactor());
                product = product.multiply(rule.getPriceFactor());
            }
        }

        // Apply all factors: estimatedPrice = ceil(basePrice * product * 10) / 10
        BigDecimal rawPrice = basePrice.multiply(product);
        BigDecimal estimatedPrice = rawPrice.multiply(BigDecimal.TEN)
                .setScale(0, RoundingMode.CEILING)
                .divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP);

        log.debug("Price estimate: distance={}m, timeSlot={}, base={}, factors={}, estimated={}",
                distanceMeters, timeSlot, basePrice, factors.keySet(), estimatedPrice);

        return new PriceEstimate(basePrice, factors, estimatedPrice, distanceMeters);
    }

    /**
     * Get AI-powered pricing suggestion based on current platform supply/demand.
     * Queries pending order count and online runner count, then calls DeepSeek.
     *
     * @return AI-generated pricing suggestion string
     */
    public String getAISuggestion() {
        // Count pending orders (status=0)
        Long pendingOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0)
        );

        // Count online (free) runners (status=0)
        Long onlineRunners = runnerMapper.selectCount(
                new LambdaQueryWrapper<Runner>().eq(Runner::getStatus, 0)
        );

        // Build context for AI
        StringBuilder context = new StringBuilder();
        context.append("当前待接单数：").append(pendingOrders).append("\n");
        context.append("当前在线跑腿员数：").append(onlineRunners).append("\n");

        String contextStr = context.toString();
        log.info("Pricing advisor context: pendingOrders={}, onlineRunners={}",
                pendingOrders, onlineRunners);

        // Call DeepSeek with pricing advisor prompt (low temperature for consistency)
        String suggestion = deepSeekClient.chat(PromptTemplates.PRICING_ADVISOR, contextStr, 0.3);
        log.info("AI pricing suggestion: {}", suggestion);
        return suggestion;
    }
}
