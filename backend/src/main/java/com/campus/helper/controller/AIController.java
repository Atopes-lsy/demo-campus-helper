package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.Result;
import com.campus.helper.entity.AIChatHistory;
import com.campus.helper.entity.AnomalyAlert;
import com.campus.helper.mapper.AIChatHistoryMapper;
import com.campus.helper.mapper.AnomalyAlertMapper;
import com.campus.helper.service.AIChatService;
import com.campus.helper.service.AnomalyDetector;
import com.campus.helper.service.SmartPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIChatService aiChatService;
    private final SmartPricingService smartPricingService;
    private final AnomalyDetector anomalyDetector;
    private final AnomalyAlertMapper anomalyAlertMapper;
    private final AIChatHistoryMapper aiChatHistoryMapper;

    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : 1L;
        String userType = body.get("userType") != null
                ? (String) body.get("userType") : "STUDENT";
        String sessionId = (String) body.get("sessionId");
        String message = (String) body.get("message");

        String reply = aiChatService.chat(userId, userType, sessionId, message);
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

    @GetMapping("/pricing/suggest/{orderId}")
    public Result getPricingSuggest(@PathVariable Long orderId) {
        Object suggestion = smartPricingService.getAISuggestion();
        return Result.success(suggestion);
    }

    @GetMapping("/anomalies")
    public Result listAnomalies(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size) {
        LambdaQueryWrapper<AnomalyAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AnomalyAlert::getDetectedAt);
        Page<AnomalyAlert> result = anomalyAlertMapper.selectPage(new Page<>(page, size), wrapper);
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
