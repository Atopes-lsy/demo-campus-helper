package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.RunnerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final OrderMapper orderMapper;
    private final RunnerMapper runnerMapper;

    @GetMapping("/overview")
    public Result overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // Today's order count and completed count in one query. Both are filtered on
        // created_at, which is what selectTodayOrderStats computes.
        Map<String, Object> todayStats = orderMapper.selectTodayOrderStats(todayStart);
        Long todayOrders = todayStats == null || todayStats.get("total") == null
                ? 0L : ((Number) todayStats.get("total")).longValue();
        Long completedToday = todayStats == null || todayStats.get("completed") == null
                ? 0L : ((Number) todayStats.get("completed")).longValue();

        // Online runners count (status = 0 or 1)
        Long onlineRunners = runnerMapper.selectCount(
                new LambdaQueryWrapper<Runner>()
                        .in(Runner::getStatus, 0, 1)
        );

        // Completion rate: completedToday / todayOrders * 100
        BigDecimal completionRate = BigDecimal.ZERO;
        if (todayOrders > 0) {
            completionRate = BigDecimal.valueOf(completedToday)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(todayOrders), 2, RoundingMode.HALF_UP);
        }

        // Today's revenue: aggregated in SQL rather than loading every completed order
        // into memory and summing in Java.
        BigDecimal todayRevenue = orderMapper.selectRevenueBetween(todayStart, todayStart.plusDays(1));
        if (todayRevenue == null) {
            todayRevenue = BigDecimal.ZERO;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("todayOrderCount", todayOrders);
        data.put("onlineRunnerCount", onlineRunners);
        data.put("completionRate", completionRate);
        data.put("todayRevenue", todayRevenue);

        return Result.success(data);
    }

    @GetMapping("/type-distribution")
    public Result typeDistribution() {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.select("order_type", "COUNT(*) AS count")
               .groupBy("order_type");
        List<Map<String, Object>> list = orderMapper.selectMaps(wrapper);
        return Result.success(list);
    }

    /**
     * Revenue trend — returns time-series data for line chart.
     *
     * @param period "day" (today hourly), "week" (last 7 days daily), "month" (last 30 days daily, default)
     * @return list of {date, revenue, orderCount} ordered by time ascending
     */
    @GetMapping("/revenue-trend")
    public Result revenueTrend(@RequestParam(defaultValue = "month") String period) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result;

        switch (period) {
            case "day" -> result = buildDayTrend(today);
            case "week" -> result = buildRangeTrend(today.minusDays(6), today, 7);
            default   -> result = buildRangeTrend(today.minusDays(29), today, 30);
        }

        return Result.success(result);
    }

    /** Hourly revenue for today (24 data points, 0-23) */
    private List<Map<String, Object>> buildDayTrend(LocalDate today) {
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        List<Order> todayOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, 5)
                        .ge(Order::getCompletedAt, dayStart)
                        .lt(Order::getCompletedAt, dayEnd));

        // Group by hour
        Map<Integer, List<Order>> byHour = todayOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getCompletedAt().getHour()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            List<Order> hourOrders = byHour.getOrDefault(h, Collections.emptyList());
            BigDecimal revenue = hourOrders.stream()
                    .map(o -> o.getAmount() != null ? o.getAmount()
                            .add(o.getTip() != null ? o.getTip() : BigDecimal.ZERO) : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", String.format("%02d:00", h));
            point.put("revenue", revenue);
            point.put("orderCount", hourOrders.size());
            result.add(point);
        }
        return result;
    }

    /** Daily revenue for a date range (one data point per day) */
    private List<Map<String, Object>> buildRangeTrend(LocalDate from, LocalDate to, int expectedDays) {
        LocalDateTime rangeStart = from.atStartOfDay();
        LocalDateTime rangeEnd = to.plusDays(1).atStartOfDay();

        List<Order> rangeOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, 5)
                        .ge(Order::getCompletedAt, rangeStart)
                        .lt(Order::getCompletedAt, rangeEnd));

        // Group by day
        Map<LocalDate, List<Order>> byDay = rangeOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getCompletedAt().toLocalDate()));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < expectedDays; i++) {
            LocalDate date = from.plusDays(i);
            List<Order> dayOrders = byDay.getOrDefault(date, Collections.emptyList());
            BigDecimal revenue = dayOrders.stream()
                    .map(o -> o.getAmount() != null ? o.getAmount()
                            .add(o.getTip() != null ? o.getTip() : BigDecimal.ZERO) : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.format(fmt));
            point.put("revenue", revenue);
            point.put("orderCount", dayOrders.size());
            result.add(point);
        }
        return result;
    }
}
