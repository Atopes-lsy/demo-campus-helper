package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.RunnerMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/runners")
public class RunnerEarningsController {

    private final RunnerMapper runnerMapper;
    private final OrderMapper orderMapper;

    public RunnerEarningsController(RunnerMapper runnerMapper, OrderMapper orderMapper) {
        this.runnerMapper = runnerMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * Get earnings and stats for a specific runner.
     * Returns total completed orders, total earnings, today's earnings
     * and the most recent completed orders.
     */
    @GetMapping("/{id}/earnings")
    public Result getEarnings(@PathVariable Long id) {
        Runner runner = runnerMapper.selectById(id);
        if (runner == null) {
            throw new BusinessException(404, "跑腿员不存在");
        }

        // All completed orders by this runner
        List<Order> completedOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getRunnerId, id)
                        .eq(Order::getStatus, 5)
                        .orderByDesc(Order::getCompletedAt)
        );

        // Total earnings
        BigDecimal totalEarnings = completedOrders.stream()
                .map(o -> o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Today's earnings
        LocalDate today = LocalDate.now();
        BigDecimal todayEarnings = completedOrders.stream()
                .filter(o -> o.getCompletedAt() != null
                        && o.getCompletedAt().toLocalDate().equals(today))
                .map(o -> o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Recent 20 completed orders
        List<Map<String, Object>> recentOrders = new ArrayList<>();
        int limit = Math.min(completedOrders.size(), 20);
        for (int i = 0; i < limit; i++) {
            Order o = completedOrders.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", o.getId());
            item.put("orderNo", o.getOrderNo());
            item.put("amount", o.getAmount());
            item.put("completedAt", o.getCompletedAt());
            recentOrders.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runnerName", runner.getName());
        result.put("rating", runner.getRating());
        result.put("totalOrders", completedOrders.size());
        result.put("totalEarnings", totalEarnings);
        result.put("todayEarnings", todayEarnings);
        result.put("recentOrders", recentOrders);

        return Result.success(result);
    }
}
