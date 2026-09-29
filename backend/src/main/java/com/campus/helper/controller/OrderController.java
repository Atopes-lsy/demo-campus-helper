package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.common.Result;
import com.campus.helper.dto.OrderCreateDTO;
import com.campus.helper.dto.OrderQueryDTO;
import com.campus.helper.dto.OrderStatusUpdateDTO;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.OrderTrackingMapper;
import com.campus.helper.service.DispatchService;
import com.campus.helper.service.OrderService;
import com.campus.helper.common.BusinessException;
import com.campus.helper.security.AuthenticatedUser;
import com.campus.helper.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderTrackingMapper orderTrackingMapper;
    private final DispatchService dispatchService;

    public OrderController(OrderService orderService,
                           OrderTrackingMapper orderTrackingMapper,
                           DispatchService dispatchService) {
        this.orderService = orderService;
        this.orderTrackingMapper = orderTrackingMapper;
        this.dispatchService = dispatchService;
    }

    @PostMapping
    public Result create(@Valid @RequestBody OrderCreateDTO dto, Authentication authentication) {
        AuthenticatedUser user = requireRole(authentication, "STUDENT");
        Order order = orderService.create(dto, user.userId());
        return Result.success(order);
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id, Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        Order order = orderService.getById(id);
        authorizeView(order, user);
        redactPickupCode(order, user);
        return Result.success(order);
    }

    @GetMapping
    public Result pageQuery(@Valid OrderQueryDTO query, Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        if ("STUDENT".equals(user.role())) {
            query.setStudentId(user.userId());
            query.setRunnerId(null);
        } else if ("RUNNER".equals(user.role())) {
            query.setStudentId(null);
            query.setRunnerId(Integer.valueOf(0).equals(query.getStatus()) ? null : user.userId());
        }
        var page = orderService.pageQuery(query);
        page.getRecords().forEach(order -> redactPickupCode(order, user));
        return Result.success(page);
    }

    @PutMapping("/{id}/status")
    public Result updateStatus(@PathVariable Long id,
                               @Valid @RequestBody OrderStatusUpdateDTO body,
                               Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        Order order = orderService.getById(id);
        authorizeTransition(order, body.getNewStatus(), user);
        orderService.updateStatus(id, body.getNewStatus(), user.userId(), user.role());
        return Result.success();
    }

    /**
     * Trigger dispatch for a pending order by hand.
     *
     * <p>Newly created orders are normally dispatched automatically by {@code DispatchConsumer};
     * this endpoint exists for demonstration and operations — most usefully to retry an order that
     * dispatch previously declined because every runner was busy, and which nobody has grabbed
     * since. ADMIN only, enforced by the path rules in {@code SecurityConfig}.
     */
    @PostMapping("/{id}/dispatch")
    public Result dispatch(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order.getStatus() != 0) {
            throw new BusinessException(409, "订单当前状态为 " + order.getStatus() + "，不是待派单状态");
        }
        Runner runner = dispatchService.dispatch(id);
        if (runner == null) {
            throw new BusinessException(409, "未能派单：没有符合条件的空闲跑腿员，或订单已被抢单者抢先处理");
        }
        return Result.success(runner);
    }

    @GetMapping("/{id}/tracking")
    public Result getTracking(@PathVariable Long id, Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        authorizeView(orderService.getById(id), user);
        List<OrderTracking> list = orderTrackingMapper.selectList(
                new LambdaQueryWrapper<OrderTracking>()
                        .eq(OrderTracking::getOrderId, id)
                        .orderByAsc(OrderTracking::getCreatedAt)
        );
        return Result.success(list);
    }

    private AuthenticatedUser requireRole(Authentication authentication, String role) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        if (!role.equals(user.role())) {
            throw new BusinessException(403, "当前角色无权执行此操作");
        }
        return user;
    }

    private void authorizeView(Order order, AuthenticatedUser user) {
        boolean allowed = SecurityUtils.isAdmin(user)
                || ("STUDENT".equals(user.role()) && user.userId().equals(order.getStudentId()))
                || ("RUNNER".equals(user.role()) && user.userId().equals(order.getRunnerId()));
        if (!allowed) {
            throw new BusinessException(403, "无权查看该订单");
        }
    }

    private void authorizeTransition(Order order, Integer newStatus, AuthenticatedUser user) {
        boolean allowed;
        if ("STUDENT".equals(user.role())) {
            allowed = user.userId().equals(order.getStudentId())
                    && ((order.getStatus() == 0 && newStatus == 6)
                    || (order.getStatus() == 4 && newStatus == 5));
        } else if ("RUNNER".equals(user.role())) {
            allowed = (order.getStatus() == 0 && newStatus == 1)
                    || (user.userId().equals(order.getRunnerId())
                    && ((order.getStatus() == 1 && (newStatus == 2 || newStatus == 3))
                    || (order.getStatus() == 2 && newStatus == 3)
                    || (order.getStatus() == 3 && newStatus == 4)));
        } else {
            allowed = SecurityUtils.isAdmin(user) && newStatus == 6
                    && order.getStatus() != 5 && order.getStatus() != 6;
        }
        if (!allowed) {
            throw new BusinessException(403, "当前用户无权执行该状态变更");
        }
    }

    private void redactPickupCode(Order order, AuthenticatedUser user) {
        if ("RUNNER".equals(user.role())
                && (!user.userId().equals(order.getRunnerId()) || order.getStatus() < 1)) {
            order.setPickupCode(null);
        }
    }
}
