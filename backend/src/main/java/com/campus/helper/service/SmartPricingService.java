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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                // TIME rules: conditionExpr is JSON like {"slots":[2,4]} or just an integer
                try {
                    if (rule.getConditionExpr().startsWith("{")) {
                        // Parse JSON format: {"slots":[2,4]}
                        JsonNode node = objectMapper.readTree(rule.getConditionExpr());
                        JsonNode slotsNode = node.get("slots");
                        if (slotsNode != null && slotsNode.isArray()) {
                            for (JsonNode slot : slotsNode) {
                                if (slot.asInt() == timeSlot) {
                                    factors.put(rule.getName(), rule.getPriceFactor());
                                    product = product.multiply(rule.getPriceFactor());
                                    break;
                                }
                            }
                        }
                    } else {
                        // Plain integer format
                        int ruleSlot = Integer.parseInt(rule.getConditionExpr());
                        if (ruleSlot == timeSlot) {
                            factors.put(rule.getName(), rule.getPriceFactor());
                            product = product.multiply(rule.getPriceFactor());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse conditionExpr '{}' for TIME rule '{}': {}",
                            rule.getConditionExpr(), rule.getName(), e.getMessage());
                }
            } else if ("DISTANCE".equals(rule.getConditionType())) {
                // DISTANCE rules: conditionExpr is JSON like {"distance_gt":1000}
                try {
                    JsonNode node = objectMapper.readTree(rule.getConditionExpr());
                    JsonNode distNode = node.get("distance_gt");
                    if (distNode != null && distanceMeters > distNode.asDouble()) {
                        factors.put(rule.getName(), rule.getPriceFactor());
                        product = product.multiply(rule.getPriceFactor());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse conditionExpr '{}' for DISTANCE rule '{}': {}",
                            rule.getConditionExpr(), rule.getName(), e.getMessage());
                }
            } else if ("DEMAND".equals(rule.getConditionType())) {
                // DEMAND rules: conditionExpr is JSON like {"pending_gt":20}
                try {
                    JsonNode node = objectMapper.readTree(rule.getConditionExpr());
                    JsonNode pendingNode = node.get("pending_gt");
                    if (pendingNode != null) {
                        Long pendingOrders = orderMapper.selectCount(
                                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0));
                        if (pendingOrders > pendingNode.asLong()) {
                            factors.put(rule.getName(), rule.getPriceFactor());
                            product = product.multiply(rule.getPriceFactor());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse conditionExpr '{}' for DEMAND rule '{}': {}",
                            rule.getConditionExpr(), rule.getName(), e.getMessage());
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
     * Get AI-powered pricing suggestion for a specific order, based on
     * current platform supply/demand.
     *
     * @param orderId the order to get pricing suggestion for
     * @return AI-generated pricing suggestion string
     */
    public String getAISuggestion(Long orderId) {
        // Queries pending order count and online runner count
        Long pendingOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0)
        );

        Long onlineRunners = runnerMapper.selectCount(
                new LambdaQueryWrapper<Runner>().eq(Runner::getStatus, 0)
        );

        // Build context with order info if available
        StringBuilder context = new StringBuilder();
        context.append("当前待接单数：").append(pendingOrders).append("\n");
        context.append("当前在线跑腿员数：").append(onlineRunners).append("\n");

        if (orderId != null) {
            Order order = orderMapper.selectById(orderId);
            if (order != null) {
                context.append("订单取货点：").append(order.getPickupLocation()).append("\n");
                context.append("订单送达点：").append(order.getDeliveryLocation()).append("\n");
                context.append("订单金额：").append(order.getAmount()).append("\n");
            }
        }

        String contextStr = context.toString();
        log.info("Pricing advisor context: pendingOrders={}, onlineRunners={}, orderId={}",
                pendingOrders, onlineRunners, orderId);

        String suggestion = deepSeekClient.chat(PromptTemplates.PRICING_ADVISOR, contextStr, 0.3);
        log.info("AI pricing suggestion: {}", suggestion);
        return suggestion;
    }
}
