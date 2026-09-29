package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.PageQuery;
import com.campus.helper.common.Result;
import com.campus.helper.entity.AIChatHistory;
import com.campus.helper.entity.AnomalyAlert;
import com.campus.helper.mapper.AIChatHistoryMapper;
import com.campus.helper.mapper.AnomalyAlertMapper;
import com.campus.helper.service.AIChatService;
import com.campus.helper.service.AnomalyDetector;
import com.campus.helper.service.DeliveryMatrixService;
import com.campus.helper.service.PricingRuleService;
import com.campus.helper.service.SmartPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIChatService aiChatService;
    private final SmartPricingService smartPricingService;
    private final PricingRuleService pricingRuleService;
    private final AnomalyDetector anomalyDetector;
    private final DeliveryMatrixService deliveryMatrixService;
    private final AnomalyAlertMapper anomalyAlertMapper;
    private final AIChatHistoryMapper aiChatHistoryMapper;

    /**
     * AI chat — ADMIN only. Supports tunable temperature and style.
     * Optional params: temperature (0.1~0.9, default 0.5), style ("precise"/"concise"/"detailed", default "detailed")
     */
    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : 1L;
        String userType = body.get("userType") != null
                ? (String) body.get("userType") : "ADMIN";
        String sessionId = (String) body.get("sessionId");
        String message = (String) body.get("message");

        // Optional tuning params
        double temperature = body.get("temperature") != null
                ? ((Number) body.get("temperature")).doubleValue() : 0.5;
        String style = body.get("style") != null
                ? (String) body.get("style") : "detailed";

        String reply = aiChatService.chat(userId, userType, sessionId, message, temperature, style);
        return Result.success(Map.of("reply", reply, "sessionId", sessionId));
    }

    @GetMapping("/chat/history/{sessionId}")
    public Result getChatHistory(@PathVariable String sessionId) {
        List<AIChatHistory> list = aiChatHistoryMapper.selectList(
                new LambdaQueryWrapper<AIChatHistory>()
                        .eq(AIChatHistory::getSessionId, sessionId)
                        .orderByAsc(AIChatHistory::getCreatedAt)
        );
        return Result.success(list);
    }

    /**
     * Get the full delivery distance matrix.
     */
    @GetMapping("/matrix")
    public Result getMatrix() {
        return Result.success(deliveryMatrixService.getMatrixOverview());
    }

    /**
     * Manually refresh the delivery distance matrix.
     */
    @PostMapping("/matrix/refresh")
    public Result refreshMatrix() {
        deliveryMatrixService.refreshMatrix();
        return Result.success(Map.of("message", "配送矩阵刷新完成"));
    }

    @GetMapping("/pricing/suggest/{orderId}")
    public Result getPricingSuggest(@PathVariable Long orderId) {
        Object suggestion = smartPricingService.getAISuggestion(orderId);
        return Result.success(suggestion);
    }

    /**
     * Apply a change to a pricing rule. The AI could suggest factors but nothing could store
     * them, which left the {@code pricing_rule} table read-only.
     *
     * <p>Inherits the ADMIN-only guard on {@code /api/ai/**}. The write evicts the cached
     * rule set once its transaction commits.
     */
    @PutMapping("/pricing/{id}")
    public Result updatePricingRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal priceFactor = body.get("priceFactor") != null
                ? new BigDecimal(body.get("priceFactor").toString()) : null;
        Boolean isActive = body.get("isActive") != null
                ? Boolean.valueOf(body.get("isActive").toString()) : null;
        String reasoning = (String) body.get("aiReasoning");
        return Result.success(pricingRuleService.updateRule(id, priceFactor, isActive, reasoning));
    }

    @GetMapping("/anomalies")
    public Result listAnomalies(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size) {
        LambdaQueryWrapper<AnomalyAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AnomalyAlert::getDetectedAt);
        Page<AnomalyAlert> result = anomalyAlertMapper.selectPage(PageQuery.of(page, size), wrapper);
        return Result.success(result);
    }

    @PutMapping("/anomalies/{id}/resolve")
    public Result resolveAnomaly(@PathVariable Long id) {
        AnomalyAlert alert = anomalyAlertMapper.selectById(id);
        if (alert != null) {
            alert.setIsResolved(1);
            anomalyAlertMapper.updateById(alert);
        }
        return Result.success();
    }

    @PostMapping("/anomalies/scan")
    public Result triggerScan() {
        anomalyDetector.scanAll();
        return Result.success(Map.of("message", "异常扫描已触发"));
    }
}
