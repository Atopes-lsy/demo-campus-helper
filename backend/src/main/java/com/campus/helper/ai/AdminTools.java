package com.campus.helper.ai;

/**
 * Admin AI tool definitions.
 * Each constant describes one tool the AI can invoke to query platform data.
 * The tool descriptions are embedded in the ADMIN_ASSISTANT system prompt.
 */
public class AdminTools {

    /** Today's operational stats: orders, revenue, completion rate, online runners */
    public static final String TODAY_STATS = "today_stats";

    /** Full delivery distance matrix: all pickup→delivery routes with average time */
    public static final String DELIVERY_MATRIX = "delivery_matrix";

    /** Estimated delivery time for a specific pickup→delivery route */
    public static final String ROUTE_ETA = "route_eta";

    /** Runner efficiency ranking: avg delivery time, rating, completed count for each runner */
    public static final String RUNNER_EFFICIENCY = "runner_efficiency";

    /** Detailed stats for a specific runner: rating trend, recent orders, earnings */
    public static final String RUNNER_DETAIL = "runner_detail";

    /** Hourly order distribution for today */
    public static final String HOURLY_DISTRIBUTION = "hourly_distribution";

    /** Revenue trend for the last 7 days */
    public static final String REVENUE_TREND = "revenue_trend";

    /** Unresolved anomaly summary grouped by type */
    public static final String ANOMALY_SUMMARY = "anomaly_summary";

    /**
     * Returns the tool list description injected into the system prompt.
     * The AI reads this to know what tools are available and how to call them.
     */
    public static String getToolsDescription() {
        return """
            ## 可用工具
            当用户的问题需要查询数据时，你必须调用工具。调用格式：在回复第一行写 TOOL: <工具名>
            如果需要参数，第二行写 ARGS: <JSON参数>

            可用工具列表：

            1. TOOL: today_stats
               查询今日运营数据（订单数、收入、完成率、在线跑腿员数）。无参数。

            2. TOOL: delivery_matrix
               查询所有取货点→送货点的配送时间矩阵。无参数。

            3. TOOL: route_eta
               查询指定取货点到指定送货点的预计配送时间。
               ARGS: {"pickup": "取货点名称", "delivery": "送货点名称"}

            4. TOOL: runner_efficiency
               查询所有跑腿员效率排名（平均配送时长、评分、完成单数）。无参数。

            5. TOOL: runner_detail
               查询某个跑腿员的详细信息（评分趋势、近期订单、收入）。
               ARGS: {"runnerName": "跑腿员姓名"}

            6. TOOL: hourly_distribution
               查询今日各时段的订单分布。无参数。

            7. TOOL: revenue_trend
               查询最近7天每日收入趋势。无参数。

            8. TOOL: anomaly_summary
               查询未处理异常预警的摘要统计。无参数。

            ## 使用规则
            - 如果用户的问题可以直接回答（如问候、功能介绍），直接回复，不要调用工具。
            - 如果用户的问题需要查询数据，先调用对应工具，等待数据返回后再生成回答。
            - 每次回复只能调用一个工具。
            - 不要在聊天中输出工具调用的技术细节，直接给出分析结果。
            - 回复简洁，不超过 300 字。数据用表格或列表呈现。
            """;
    }

    private AdminTools() {}
}
