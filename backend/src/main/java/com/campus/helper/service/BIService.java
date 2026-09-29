package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.cache.CacheKeys;
import com.campus.helper.cache.CacheService;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.Runner;
import com.campus.helper.entity.AnomalyAlert;
import com.campus.helper.mapper.AnomalyAlertMapper;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.RunnerMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business Intelligence service — powers the admin dashboard.
 *
 * Data sources:
 * <ul>
 *   <li>Elasticsearch — heavy aggregations (trends, distributions, heatmaps)</li>
 *   <li>MySQL — real-time running state (online runners, live metrics)</li>
 * </ul>
 *
 * Design principle: ES handles analytics, MySQL handles operational state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BIService {

    /** Dashboards tolerate a few minutes of staleness; admins read them on their own cadence. */
    private static final Duration DASHBOARD_TTL = Duration.ofMinutes(5);
    private static final Duration DASHBOARD_JITTER = Duration.ofMinutes(1);

    private final OrderSearchService orderSearchService;
    private final OrderMapper orderMapper;
    private final RunnerMapper runnerMapper;
    private final AnomalyAlertMapper anomalyAlertMapper;
    private final CacheService cacheService;

    // ── Dashboard Overview ─────────────────────────────

    /**
     * Real-time overview combining MySQL (live metrics) + ES (trends).
     */
    public Map<String, Object> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // MySQL: real-time operational data
        Long todayOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().ge(Order::getCreatedAt, todayStart));

        Long onlineRunners = runnerMapper.selectCount(
                new LambdaQueryWrapper<Runner>().in(Runner::getStatus, 0, 1));

        Long completedToday = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .ge(Order::getCompletedAt, todayStart)
                        .eq(Order::getStatus, 5));

        BigDecimal completionRate = BigDecimal.ZERO;
        if (todayOrders > 0) {
            completionRate = BigDecimal.valueOf(completedToday)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(todayOrders), 2, RoundingMode.HALF_UP);
        }

        // MySQL: today's revenue, aggregated in SQL. Loading every completed order and
        // summing in Java grew without bound as order history accumulated.
        BigDecimal todayRevenue = orderMapper.selectRevenueBetween(todayStart, todayStart.plusDays(1));
        if (todayRevenue == null) {
            todayRevenue = BigDecimal.ZERO;
        }

        // MySQL: anomaly count
        Long unresolvedAnomalies = anomalyAlertMapper.selectCount(
                new LambdaQueryWrapper<AnomalyAlert>()
                        .eq(AnomalyAlert::getIsResolved, 0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayOrderCount", todayOrders);
        result.put("onlineRunnerCount", onlineRunners);
        result.put("completedToday", completedToday);
        result.put("completionRate", completionRate);
        result.put("todayRevenue", todayRevenue);
        result.put("unresolvedAnomalies", unresolvedAnomalies);

        // ES: recent 7-day trend (last 7 data points for mini chart)
        List<Map<String, Object>> weekTrend = orderSearchService.revenueTrend(7, "week");
        result.put("weekTrend", weekTrend);

        return result;
    }

    // ── BI Dashboard (full ES aggregation) ────────────

    /**
     * Full BI dashboard data from Elasticsearch.
     * Includes: revenue, status distribution, type distribution,
     * daily trend, hourly peak, and geo heatmap.
     *
     * @param days lookback window (7, 30, 90)
     */
    public Map<String, Object> dashboard(int days) {
        // Cached with a jittered TTL, and deliberately NOT invalidated by order events: the
        // dashboard aggregates a 7/30/90-day window, where a single new order moves the
        // numbers negligibly, while order events arrive far more often than an admin looks
        // at this page. Evicting per event would spend the cache without buying freshness.
        return cacheService.getOrLoad(CacheKeys.biDashboard(days),
                new TypeReference<Map<String, Object>>() {
                },
                DASHBOARD_TTL, DASHBOARD_JITTER,
                () -> {
                    Map<String, Object> esData = orderSearchService.biDashboard(days);

                    // Enrich with MySQL real-time data
                    Long onlineRunners = runnerMapper.selectCount(
                            new LambdaQueryWrapper<Runner>().in(Runner::getStatus, 0, 1));
                    esData.put("onlineRunners", onlineRunners);
                    return esData;
                });
    }

    // ── Revenue Analysis ──────────────────────────────

    /**
     * Revenue analysis with trend + summary stats.
     *
     * @param period "day" | "week" | "month"
     */
    public Map<String, Object> revenueAnalysis(String period) {
        int days;
        String interval;
        switch (period) {
            case "day"  -> { days = 1;  interval = "day"; }
            case "week" -> { days = 7;  interval = "week"; }
            default     -> { days = 30; interval = "month"; }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);

        // Trend from ES
        List<Map<String, Object>> trend = orderSearchService.revenueTrend(days, interval);

        // Summary stats
        double totalRevenue = trend.stream()
                .mapToDouble(m -> ((Number) m.getOrDefault("revenue", 0.0)).doubleValue())
                .sum();
        long totalOrders = trend.stream()
                .mapToLong(m -> ((Number) m.getOrDefault("orderCount", 0L)).longValue())
                .sum();
        double avgRevenue = totalOrders > 0 ? totalRevenue / totalOrders : 0.0;

        result.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        result.put("totalOrders", totalOrders);
        result.put("avgRevenuePerOrder", Math.round(avgRevenue * 100.0) / 100.0);
        result.put("trend", trend);

        return result;
    }

    // ── Runner Performance ────────────────────────────

    /**
     * Runner performance ranking from MySQL.
     * Top N runners by completed orders and total earnings.
     */
    public Map<String, Object> runnerRanking(int topN) {
        List<Runner> allRunners = runnerMapper.selectList(null);

        // Load every runner's earnings in one query. Doing this per runner inside the map
        // below cost 1+N queries, growing linearly with the number of runners.
        Map<Long, BigDecimal> earningsByRunner = new HashMap<>();
        for (Map<String, Object> row : orderMapper.selectEarningsByRunner()) {
            Long runnerId = ((Number) row.get("runner_id")).longValue();
            BigDecimal earnings = row.get("total_earnings") != null
                    ? new BigDecimal(row.get("total_earnings").toString()) : BigDecimal.ZERO;
            earningsByRunner.put(runnerId, earnings);
        }

        List<Map<String, Object>> ranking = allRunners.stream()
                .map(r -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("runnerId", r.getId());
                    entry.put("name", r.getName());
                    entry.put("rating", r.getRating());
                    entry.put("completedOrders", r.getCompletedOrders() != null
                            ? r.getCompletedOrders() : 0);
                    entry.put("status", r.getStatus());
                    entry.put("totalEarnings",
                            earningsByRunner.getOrDefault(r.getId(), BigDecimal.ZERO));

                    return entry;
                })
                .sorted((a, b) -> {
                    int cmp = ((Integer) b.get("completedOrders"))
                            .compareTo((Integer) a.get("completedOrders"));
                    if (cmp != 0) return cmp;
                    return ((BigDecimal) b.get("totalEarnings"))
                            .compareTo((BigDecimal) a.get("totalEarnings"));
                })
                .limit(topN)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", ranking);

        // Overall stats
        long totalCompleted = allRunners.stream()
                .mapToInt(r -> r.getCompletedOrders() != null ? r.getCompletedOrders() : 0)
                .sum();
        result.put("totalCompletedOrders", totalCompleted);
        result.put("totalRunners", allRunners.size());

        return result;
    }

    // ── Combined Distribution & Trend ─────────────────

    /**
     * Combined status & type distribution from ES.
     */
    public Map<String, Object> distribution(int days) {
        List<Map<String, Object>> byStatus = orderSearchService.statusDistribution(days);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byStatus", byStatus);
        result.put("statusLabels", Map.of(
                0, "待接单", 1, "已派单", 2, "已取件",
                3, "配送中", 4, "已送达", 5, "已完成", 6, "已取消"
        ));
        return result;
    }

    /**
     * Geo-heatmap data for map visualization.
     */
    public Map<String, Object> heatmap(int days) {
        List<Map<String, Object>> heatmapData = orderSearchService.geoHeatmap(days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("heatmap", heatmapData);
        return result;
    }
}
