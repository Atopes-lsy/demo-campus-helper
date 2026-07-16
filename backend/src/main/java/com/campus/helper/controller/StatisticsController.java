package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.PaymentMapper;
import com.campus.helper.mapper.RunnerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final OrderMapper orderMapper;
    private final RunnerMapper runnerMapper;
    private final PaymentMapper paymentMapper;

    @GetMapping("/overview")
    public Result overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // Today's orders count
        Long todayOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreatedAt, todayStart)
        );

        // Online runners count (status = 0 or 1)
        Long onlineRunners = runnerMapper.selectCount(
                new LambdaQueryWrapper<Runner>()
                        .in(Runner::getStatus, 0, 1)
        );

        // Completed orders today
        Long completedToday = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreatedAt, todayStart)
                        .eq(Order::getStatus, 5)
        );

        // Completion rate: completedToday / todayOrders * 100
        BigDecimal completionRate = BigDecimal.ZERO;
        if (todayOrders > 0) {
            completionRate = BigDecimal.valueOf(completedToday)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(todayOrders), 2, RoundingMode.HALF_UP);
        }

        // Today's revenue from PaymentMapper overview query
        Map<String, Object> overview = paymentMapper.selectOverview();
        BigDecimal todayRevenue = BigDecimal.ZERO;
        if (overview != null && overview.get("total") != null) {
            todayRevenue = (BigDecimal) overview.get("total");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("todayOrders", todayOrders);
        data.put("onlineRunners", onlineRunners);
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
}
