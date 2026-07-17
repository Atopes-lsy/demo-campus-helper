package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.ai.AdminTools;
import com.campus.helper.ai.DeepSeekClient;
import com.campus.helper.ai.PromptTemplates;
import com.campus.helper.entity.*;
import com.campus.helper.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Admin-specific AI service with tool-calling capability.
 *
 * Two-round DeepSeek calling pattern:
 * Round 1: Send user question + tools description → AI decides which tool to call
 * Round 2 (if tool called): Execute SQL query, feed result back → AI generates natural answer
 *
 * This approach works without native function-calling API support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAIService {

    private final DeepSeekClient deepSeekClient;
    private final AIChatHistoryMapper aiChatHistoryMapper;
    private final DeliveryMatrixService deliveryMatrixService;
    private final OrderMapper orderMapper;
    private final RunnerMapper runnerMapper;
    private final StudentMapper studentMapper;
    private final PaymentMapper paymentMapper;
    private final AnomalyAlertMapper anomalyAlertMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main entry for admin AI chat with default parameters.
     */
    public String adminChat(Long userId, String sessionId, String message) {
        return adminChat(userId, sessionId, message, 0.5, "balanced");
    }

    /**
     * Main entry for admin AI chat with tunable parameters.
     * Handles tool calling, executes queries, and returns natural language responses.
     *
     * @param userId      the admin user ID
     * @param sessionId   the chat session identifier
     * @param message     the user's message
     * @param temperature AI temperature (0.1=precise 0.9=creative)
     * @param style       response style: "precise" | "concise" | "detailed"
     */
    public String adminChat(Long userId, String sessionId, String message,
                            double temperature, String style) {
        // Save user message
        saveHistory(sessionId, userId, "ADMIN", "user", message);

        // Build style-specific instruction
        String styleInstruction = buildStyleInstruction(style);

        // Round 1: Ask AI with tools
        String systemPrompt = PromptTemplates.ADMIN_ASSISTANT + "\n\n"
                + styleInstruction + "\n\n"
                + AdminTools.getToolsDescription();
        String firstResponse = deepSeekClient.chat(systemPrompt, message, temperature);

        // Check if AI wants to call a tool
        String toolName = extractToolCall(firstResponse);
        if (toolName == null) {
            // AI answered directly (greeting, general question, etc.)
            saveHistory(sessionId, userId, "ADMIN", "assistant", firstResponse);
            return firstResponse;
        }

        // Parse args if any
        String argsJson = extractArgs(firstResponse);

        // Execute the tool
        String toolResult = executeTool(toolName, argsJson);

        if (toolResult == null || toolResult.isEmpty()) {
            String fallback = "抱歉，未能查询到相关数据。请检查查询条件或稍后再试。";
            saveHistory(sessionId, userId, "ADMIN", "assistant", fallback);
            return fallback;
        }

        // Round 2: Feed tool result back to AI for natural language generation
        String round2Instruction = buildStyleInstruction(style);
        String contextMsg = round2Instruction + "\n\n用户问题：" + message
                + "\n\n工具 " + toolName + " 查询结果：\n" + toolResult;
        String finalResponse = deepSeekClient.chat(PromptTemplates.ADMIN_ASSISTANT, contextMsg, temperature);

        saveHistory(sessionId, userId, "ADMIN", "assistant", finalResponse);
        return finalResponse;
    }

    /**
     * Build a style-specific instruction appended to the system prompt.
     */
    private String buildStyleInstruction(String style) {
        if ("precise".equals(style)) {
            return "【回复风格：精确数据】请用精确的数字和结构化格式呈现数据，使用表格或分点列表，保持客观严谨。";
        } else if ("concise".equals(style)) {
            return "【回复风格：简洁概述】请用最精炼的语言概括关键信息，控制在150字以内，只保留最重要的数据。";
        } else {
            // "detailed" (default)
            return "【回复风格：详细分析】请提供完整的数据分析，包括趋势解读、对比分析和改进建议，可以适当展开说明。";
        }
    }

    // ---- Tool execution ----

    private String executeTool(String toolName, String argsJson) {
        try {
            Map<String, Object> args = argsJson != null && !argsJson.isEmpty()
                    ? objectMapper.readValue(argsJson, Map.class) : Collections.emptyMap();

            return switch (toolName) {
                case AdminTools.TODAY_STATS -> executeTodayStats();
                case AdminTools.DELIVERY_MATRIX -> executeDeliveryMatrix();
                case AdminTools.ROUTE_ETA -> executeRouteETA(args);
                case AdminTools.RUNNER_EFFICIENCY -> executeRunnerEfficiency();
                case AdminTools.RUNNER_DETAIL -> executeRunnerDetail(args);
                case AdminTools.HOURLY_DISTRIBUTION -> executeHourlyDistribution();
                case AdminTools.REVENUE_TREND -> executeRevenueTrend();
                case AdminTools.ANOMALY_SUMMARY -> executeAnomalySummary();
                default -> "未知工具：" + toolName;
            };
        } catch (Exception e) {
            log.error("Tool execution failed: tool={}, args={}, error={}", toolName, argsJson, e.getMessage());
            return null;
        }
    }

    // ---- Tool implementations ----

    private String executeTodayStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        // Today's orders
        Long todayOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().ge(Order::getCreatedAt, todayStart).lt(Order::getCreatedAt, todayEnd));
        // Today's completed
        Long completedOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 5)
                        .ge(Order::getCompletedAt, todayStart).lt(Order::getCompletedAt, todayEnd));

        // Today's revenue (sum of amount for completed orders today)
        List<Order> completedToday = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 5)
                        .ge(Order::getCompletedAt, todayStart).lt(Order::getCompletedAt, todayEnd));
        BigDecimal todayRevenue = completedToday.stream()
                .map(o -> o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Online runners
        Long onlineRunners = runnerMapper.selectCount(
                new LambdaQueryWrapper<Runner>().eq(Runner::getStatus, 0));

        // Pending orders
        Long pendingOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0));

        // Completion rate
        String completionRate = todayOrders > 0
                ? String.format("%.0f%%", (double) completedOrders / todayOrders * 100)
                : "暂无数据";

        return String.format(
                "今日新增订单：%d 单 | 已完成：%d 单 | 完成率：%s | 今日收入：¥%s | 待接单：%d 单 | 在线跑腿员：%d 人",
                todayOrders, completedOrders, completionRate, todayRevenue.toPlainString(),
                pendingOrders, onlineRunners
        );
    }

    private String executeDeliveryMatrix() {
        List<Map<String, Object>> matrix = deliveryMatrixService.getMatrixOverview();
        if (matrix.isEmpty()) {
            return "暂无配送矩阵数据，请等待系统积累足够的已完成订单后自动生成。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("配送距离矩阵（按平均时间升序）：\n");
        for (Map<String, Object> row : matrix) {
            sb.append("- ").append(row.get("pickup_location"))
                    .append(" → ").append(row.get("delivery_location"))
                    .append("：平均 ").append(row.get("avg_minutes")).append(" 分钟")
                    .append("（" + row.get("order_count") + " 单）\n");
        }
        return sb.toString().trim();
    }

    private String executeRouteETA(Map<String, Object> args) {
        String pickup = (String) args.getOrDefault("pickup", "");
        String delivery = (String) args.getOrDefault("delivery", "");

        if (pickup.isEmpty()) {
            return "请指定取货点名称。";
        }
        if (delivery.isEmpty()) {
            // Only pickup given, show all routes from that pickup
            List<Map<String, Object>> rows = deliveryMatrixService.getMatrixOverview();
            StringBuilder sb = new StringBuilder();
            sb.append("从「").append(pickup).append("」出发的路线：\n");
            boolean found = false;
            for (Map<String, Object> row : rows) {
                if (pickup.equals(row.get("pickup_location"))) {
                    sb.append("- → ").append(row.get("delivery_location"))
                            .append("：平均 ").append(row.get("avg_minutes")).append(" 分钟\n");
                    found = true;
                }
            }
            if (!found) {
                return "未找到从「" + pickup + "」出发的历史配送数据。";
            }
            return sb.toString().trim();
        }

        String eta = deliveryMatrixService.getETADescription(pickup, delivery);
        if (eta != null) {
            return eta;
        }
        return "暂无路线「" + pickup + " → " + delivery + "」的配送数据，可能还没有完成过该路线的订单。";
    }

    private String executeRunnerEfficiency() {
        List<Runner> allRunners = runnerMapper.selectList(new LambdaQueryWrapper<>());
        if (allRunners.isEmpty()) {
            return "暂无跑腿员数据。";
        }

        // Build efficiency data for each runner
        List<Map<String, Object>> efficiencyList = new ArrayList<>();
        for (Runner runner : allRunners) {
            // Count completed orders
            Long completed = orderMapper.selectCount(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getRunnerId, runner.getId())
                            .eq(Order::getStatus, 5));
            // Avg delivery time (reuse existing mapper method)
            Double avgMinutes = orderMapper.selectAvgDeliveryMinutes(runner.getId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", runner.getName());
            item.put("rating", runner.getRating());
            item.put("completedOrders", completed);
            item.put("avgMinutes", avgMinutes != null ? Math.round(avgMinutes) : null);

            efficiencyList.add(item);
        }

        // Sort by a composite score: completed_orders / avg_minutes (higher is better)
        efficiencyList.sort((a, b) -> {
            double scoreA = getEfficiencyScore(a);
            double scoreB = getEfficiencyScore(b);
            return Double.compare(scoreB, scoreA);
        });

        StringBuilder sb = new StringBuilder();
        sb.append("跑腿员效率排名：\n");
        int rank = 1;
        for (Map<String, Object> item : efficiencyList) {
            sb.append(rank++).append(". ").append(item.get("name"))
                    .append(" | 评分：").append(item.get("rating"))
                    .append(" | 完成：").append(item.get("completedOrders")).append(" 单");
            if (item.get("avgMinutes") != null) {
                sb.append(" | 平均配送：").append(item.get("avgMinutes")).append(" 分钟");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private double getEfficiencyScore(Map<String, Object> item) {
        double completed = ((Number) item.getOrDefault("completedOrders", 0)).doubleValue();
        Object avgMinObj = item.get("avgMinutes");
        double avgMin = avgMinObj != null ? ((Number) avgMinObj).doubleValue() : 999;
        return completed / Math.max(avgMin, 1);
    }

    private String executeRunnerDetail(Map<String, Object> args) {
        String runnerName = (String) args.getOrDefault("runnerName", "");
        if (runnerName.isEmpty()) {
            return "请提供跑腿员姓名，例如：ARGS: {\"runnerName\": \"张三\"}";
        }

        Runner runner = runnerMapper.selectOne(
                new LambdaQueryWrapper<Runner>().eq(Runner::getName, runnerName));
        if (runner == null) {
            return "未找到跑腿员「" + runnerName + "」。";
        }

        Long completed = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getRunnerId, runner.getId())
                        .eq(Order::getStatus, 5));
        Double avgMinutes = orderMapper.selectAvgDeliveryMinutes(runner.getId());

        // Recent 5 orders
        List<Order> recentOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getRunnerId, runner.getId())
                        .orderByDesc(Order::getCompletedAt)
                        .last("LIMIT 5"));

        StringBuilder sb = new StringBuilder();
        sb.append("跑腿员「").append(runnerName).append("」详情：\n");
        sb.append("- 评分：").append(runner.getRating()).append(" / 5.0\n");
        sb.append("- 已完成：").append(completed).append(" 单\n");
        if (avgMinutes != null) {
            sb.append("- 平均配送时长：").append(Math.round(avgMinutes)).append(" 分钟\n");
        }
        sb.append("- 当前状态：").append(runner.getStatus() == 0 ? "空闲" : runner.getStatus() == 1 ? "接单中" : "休息").append("\n");
        if (!recentOrders.isEmpty()) {
            sb.append("- 最近订单：\n");
            for (Order o : recentOrders) {
                sb.append("  · ").append(o.getPickupLocation()).append(" → ").append(o.getDeliveryLocation())
                        .append(" (").append(o.getAmount()).append("元)\n");
            }
        }
        return sb.toString().trim();
    }

    private String executeHourlyDistribution() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        List<Order> todayOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreatedAt, todayStart)
                        .lt(Order::getCreatedAt, todayEnd));

        // Group by hour
        int[] hourlyCounts = new int[24];
        for (Order o : todayOrders) {
            int hour = o.getCreatedAt().getHour();
            hourlyCounts[hour]++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("今日各时段订单分布：\n");
        for (int i = 0; i < 24; i++) {
            if (hourlyCounts[i] > 0) {
                sb.append(String.format("- %02d:00-%02d:00：%d 单\n", i, i + 1, hourlyCounts[i]));
            }
        }
        if (todayOrders.isEmpty()) {
            sb.append("今日暂无订单。");
        }
        return sb.toString().trim();
    }

    private String executeRevenueTrend() {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("近7天收入趋势：\n");

        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            List<Order> dayOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getStatus, 5)
                            .ge(Order::getCompletedAt, dayStart)
                            .lt(Order::getCompletedAt, dayEnd));

            BigDecimal dayRevenue = dayOrders.stream()
                    .map(o -> o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long dayCount = dayOrders.size();
            totalRevenue = totalRevenue.add(dayRevenue);

            String marker = date.equals(today) ? " ← 今天" : "";
            sb.append(String.format("- %s：%d 单，¥%s%s\n",
                    date.format(DateTimeFormatter.ofPattern("MM/dd")),
                    dayCount, dayRevenue.toPlainString(), marker));
        }
        sb.append(String.format("\n7天合计收入：¥%s", totalRevenue.toPlainString()));
        return sb.toString().trim();
    }

    private String executeAnomalySummary() {
        List<AnomalyAlert> unresolved = anomalyAlertMapper.selectList(
                new LambdaQueryWrapper<AnomalyAlert>()
                        .eq(AnomalyAlert::getIsResolved, 0)
                        .orderByDesc(AnomalyAlert::getDetectedAt));

        if (unresolved.isEmpty()) {
            return "暂无未处理的异常预警。✅";
        }

        // Group by type
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (AnomalyAlert a : unresolved) {
            byType.merge(a.getAnomalyType(), 1L, Long::sum);
            bySeverity.merge(severityLabel(a.getSeverity()), 1L, Long::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("未处理异常摘要（共 ").append(unresolved.size()).append(" 条）：\n");
        sb.append("按类型：\n");
        for (Map.Entry<String, Long> e : byType.entrySet()) {
            sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append(" 条\n");
        }
        sb.append("按严重程度：\n");
        for (Map.Entry<String, Long> e : bySeverity.entrySet()) {
            sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append(" 条\n");
        }

        // Add latest 3
        sb.append("\n最新异常：\n");
        int count = 0;
        for (AnomalyAlert a : unresolved) {
            if (count++ >= 3) break;
            sb.append("- [").append(severityLabel(a.getSeverity())).append("] ")
                    .append(a.getDescription()).append("\n");
        }
        return sb.toString().trim();
    }

    private String severityLabel(int severity) {
        return switch (severity) {
            case 1 -> "低";
            case 2 -> "中";
            case 3 -> "高";
            default -> "未知";
        };
    }

    // ---- Helpers ----

    private String extractToolCall(String aiResponse) {
        if (aiResponse == null) return null;
        for (String line : aiResponse.split("\n")) {
            line = line.trim();
            if (line.startsWith("TOOL:") || line.startsWith("TOOL：")) {
                return line.substring(line.indexOf(":") + 1).trim().replace("：", "").trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractArgs(String aiResponse) {
        if (aiResponse == null) return null;
        boolean foundTool = false;
        for (String line : aiResponse.split("\n")) {
            line = line.trim();
            if (line.startsWith("TOOL:") || line.startsWith("TOOL：")) {
                foundTool = true;
                continue;
            }
            if (foundTool && (line.startsWith("ARGS:") || line.startsWith("ARGS："))) {
                String argsStr = line.substring(line.indexOf(":") + 1).trim().replace("：", "").trim();
                // Handle both JSON and simple formats
                if (argsStr.startsWith("{")) {
                    return argsStr;
                }
                // Try to parse simple key=value pairs into JSON
                Map<String, String> simpleArgs = new LinkedHashMap<>();
                for (String part : argsStr.split(",")) {
                    String[] kv = part.trim().split("=", 2);
                    if (kv.length == 2) {
                        simpleArgs.put(kv[0].trim(), kv[1].trim().replace("\"", ""));
                    }
                }
                if (!simpleArgs.isEmpty()) {
                    try {
                        return objectMapper.writeValueAsString(simpleArgs);
                    } catch (Exception ignored) {}
                }
                return null;
            }
        }
        return null;
    }

    private void saveHistory(String sessionId, Long userId, String userType, String role, String content) {
        AIChatHistory history = new AIChatHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserType(userType);
        history.setRole(role);
        history.setContent(content);
        history.setCreatedAt(LocalDateTime.now());
        aiChatHistoryMapper.insert(history);
    }
}
