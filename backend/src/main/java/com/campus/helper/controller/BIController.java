package com.campus.helper.controller;

import com.campus.helper.common.Result;
import com.campus.helper.service.BIService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * BI (Business Intelligence) dashboard API for admins.
 *
 * Powered by Elasticsearch aggregations (analytics) + MySQL (operational state).
 *
 * Endpoints:
 * <ul>
 *   <li>GET /api/bi/overview        — real-time KPI cards</li>
 *   <li>GET /api/bi/dashboard       — full dashboard (status, type, trend, heatmap)</li>
 *   <li>GET /api/bi/revenue         — revenue analysis with trend chart</li>
 *   <li>GET /api/bi/distribution    — order status & type distribution (pie/bar chart)</li>
 *   <li>GET /api/bi/runners         — runner performance ranking</li>
 *   <li>GET /api/bi/heatmap         — geo-heatmap data for map visualization</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/bi")
@RequiredArgsConstructor
public class BIController {

    private final BIService biService;

    /**
     * Real-time overview — KPI cards at the top of the dashboard.
     * Returns: todayOrderCount, onlineRunnerCount, completionRate,
     *          todayRevenue, unresolvedAnomalies, weekTrend
     */
    @GetMapping("/overview")
    public Result overview() {
        return Result.success(biService.overview());
    }

    /**
     * Full BI dashboard with all dimensions.
     *
     * @param days  lookback window (7, 30, 90), default 30
     */
    @GetMapping("/dashboard")
    public Result dashboard(@RequestParam(defaultValue = "30") int days) {
        return Result.success(biService.dashboard(days));
    }

    /**
     * Revenue analysis — trend chart data + summary stats.
     *
     * @param period  "day" (hourly), "week" (last 7 days), "month" (last 30 days)
     */
    @GetMapping("/revenue")
    public Result revenue(@RequestParam(defaultValue = "month") String period) {
        return Result.success(biService.revenueAnalysis(period));
    }

    /**
     * Status & type distribution for pie/bar charts.
     *
     * @param days  lookback window, default 30
     */
    @GetMapping("/distribution")
    public Result distribution(@RequestParam(defaultValue = "30") int days) {
        return Result.success(biService.distribution(days));
    }

    /**
     * Runner performance ranking.
     *
     * @param topN  number of top runners to return, default 10
     */
    @GetMapping("/runners")
    public Result runnerRanking(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(biService.runnerRanking(topN));
    }

    /**
     * Geo-heatmap data for map visualization (geohash grid).
     *
     * @param days  lookback window, default 30
     */
    @GetMapping("/heatmap")
    public Result heatmap(@RequestParam(defaultValue = "30") int days) {
        return Result.success(biService.heatmap(days));
    }

    /**
     * Legacy compatibility: redirect old /api/statistics endpoints to BI.
     * Returns the same overview data as /overview.
     */
    @GetMapping("/legacy/overview")
    public Result legacyOverview() {
        Map<String, Object> data = biService.overview();
        // Remove ES-specific fields for legacy compatibility
        data.remove("weekTrend");
        return Result.success(data);
    }
}
