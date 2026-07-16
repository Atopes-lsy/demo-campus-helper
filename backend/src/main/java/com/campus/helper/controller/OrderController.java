package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.common.Result;
import com.campus.helper.dto.OrderCreateDTO;
import com.campus.helper.dto.OrderQueryDTO;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.mapper.OrderTrackingMapper;
import com.campus.helper.service.DispatchService;
import com.campus.helper.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DispatchService dispatchService;
    private final OrderTrackingMapper orderTrackingMapper;

    public OrderController(OrderService orderService,
                           DispatchService dispatchService,
                           OrderTrackingMapper orderTrackingMapper) {
        this.orderService = orderService;
        this.dispatchService = dispatchService;
        this.orderTrackingMapper = orderTrackingMapper;
    }

    @PostMapping
    public Result create(@Valid @RequestBody OrderCreateDTO dto) {
        Order order = orderService.create(dto);
        // No longer auto-dispatch — orders stay at status 0 (pending)
        // for runners to browse and accept manually.
        // try {
        //     dispatchService.dispatch(order.getId());
        // } catch (Exception e) {
        //     // dispatch failure should not fail the order creation
        // }
        return Result.success(order);
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        Order order = orderService.getById(id);
        return Result.success(order);
    }

    @GetMapping
    public Result pageQuery(OrderQueryDTO query) {
        return Result.success(orderService.pageQuery(query));
    }

    @PutMapping("/{id}/status")
    public Result updateStatus(@PathVariable Long id,
                               @RequestBody Map<String, Object> body) {
        Integer newStatus = (Integer) body.get("newStatus");
        Long operatorId = body.get("operatorId") != null
                ? ((Number) body.get("operatorId")).longValue() : null;
        String operatorType = (String) body.get("operatorType");
        orderService.updateStatus(id, newStatus, operatorId, operatorType);
        return Result.success();
    }

    @GetMapping("/{id}/tracking")
    public Result getTracking(@PathVariable Long id) {
        List<OrderTracking> list = orderTrackingMapper.selectList(
                new LambdaQueryWrapper<OrderTracking>()
                        .eq(OrderTracking::getOrderId, id)
                        .orderByAsc(OrderTracking::getCreatedAt)
        );
        return Result.success(list);
    }
}
