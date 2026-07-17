package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.StatusFlowValidator;
import com.campus.helper.dto.OrderCreateDTO;
import com.campus.helper.dto.PackageItemDTO;
import com.campus.helper.dto.OrderQueryDTO;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.entity.Runner;
import com.campus.helper.entity.Student;
import com.campus.helper.entity.Payment;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.OrderTrackingMapper;
import com.campus.helper.mapper.RunnerMapper;
import com.campus.helper.mapper.StudentMapper;
import com.campus.helper.mapper.PaymentMapper;
import com.campus.helper.websocket.NotificationHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderTrackingMapper orderTrackingMapper;
    private final StudentMapper studentMapper;
    private final RunnerMapper runnerMapper;
    private final NotificationHandler notificationHandler;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Price per package size: small=4, medium=5, large=8 */
    private static final Map<String, BigDecimal> PACKAGE_PRICE = Map.of(
            "small", new BigDecimal("4"),
            "medium", new BigDecimal("5"),
            "large", new BigDecimal("8")
    );

    /** Size display names used in auto-generated titles */
    private static final Map<String, String> SIZE_LABEL = Map.of(
            "small", "小件",
            "medium", "中件",
            "large", "大件"
    );

    /**
     * Paginated query for orders with dynamic filters.
     */
    public Page<Order> pageQuery(OrderQueryDTO query) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(query.getOrderType() != null, Order::getOrderType, query.getOrderType())
               .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
               .eq(query.getStudentId() != null, Order::getStudentId, query.getStudentId())
               .eq(query.getRunnerId() != null, Order::getRunnerId, query.getRunnerId());

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Order::getTitle, query.getKeyword());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(Order::getCreatedAt, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.le(Order::getCreatedAt, query.getEndDate().atTime(23, 59, 59));
        }

        wrapper.orderByDesc(Order::getCreatedAt);

        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        return orderMapper.selectPage(page, wrapper);
    }

    /**
     * Get order by ID, throwing 404 if not found.
     */
    public Order getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        return order;
    }

    /**
     * Create a new order with auto-generated order number and title.
     * <p>
     * Amount is calculated automatically from package sizes:
     *   small=¥4, medium=¥5, large=¥8.
     * Title is auto-generated from package count, total amount,
     * pickup location, and delivery location.
     */
    @Transactional
    public Order create(OrderCreateDTO dto) {
        Order order = new Order();

        String orderNo = "ORD"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(10000, 100000);
        order.setOrderNo(orderNo);

        // Calculate total amount from package sizes
        BigDecimal totalAmount = BigDecimal.ZERO;
        StringBuilder sizeSummary = new StringBuilder();
        for (PackageItemDTO pkg : dto.getPackages()) {
            BigDecimal price = PACKAGE_PRICE.getOrDefault(pkg.getSize(), BigDecimal.ZERO);
            totalAmount = totalAmount.add(price);
            if (sizeSummary.length() > 0) {
                sizeSummary.append("+");
            }
            sizeSummary.append(SIZE_LABEL.getOrDefault(pkg.getSize(), pkg.getSize()));
        }
        order.setAmount(totalAmount);

        // Auto-generate title: "N件包裹 ¥total pickup→delivery" (only admin/runner can see)
        String title = dto.getPackages().size() + "件包裹（" + sizeSummary + "）"
                + " ¥" + totalAmount
                + " " + dto.getPickupLocation()
                + "→" + dto.getDeliveryLocation();
        order.setTitle(title);

        // Store packages as JSON
        try {
            order.setPackages(objectMapper.writeValueAsString(dto.getPackages()));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "Failed to serialize packages");
        }

        order.setPickupCode(dto.getPickupCode());
        order.setPickupLocation(dto.getPickupLocation());
        order.setDeliveryLocation(dto.getDeliveryLocation());
        order.setOrderType(dto.getOrderType());
        order.setTip(dto.getTip() != null ? dto.getTip() : BigDecimal.ZERO);
        order.setStatus(0);

        // Use student ID from the request (authenticated user)
        Long studentId = dto.getStudentId();
        order.setStudentId(studentId);

        orderMapper.insert(order);

        // Auto-create payment record tied to this order
        Payment payment = new Payment();
        payment.setPaymentNo("PAY"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(1000, 10000));
        payment.setOrderId(order.getId());
        payment.setPayerId(studentId);
        payment.setPayeeId(0L); // runner not yet assigned
        payment.setAmount(totalAmount.add(order.getTip() != null ? order.getTip() : BigDecimal.ZERO));
        payment.setPayStatus(0); // pending payment
        paymentMapper.insert(payment);

        // Insert initial tracking record
        OrderTracking tracking = new OrderTracking();
        tracking.setOrderId(order.getId());
        tracking.setStatusFrom(null);
        tracking.setStatusTo(0);
        tracking.setDescription("学生提交订单");
        tracking.setOperatorId(studentId);
        tracking.setOperatorType("STUDENT");
        orderTrackingMapper.insert(tracking);

        // Check student exists and is active
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学生账号不存在");
        }
        if (student.getStatus() != null && student.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，无法创建订单");
        }

        // Increment student total_orders
        student.setTotalOrders(student.getTotalOrders() + 1);
        studentMapper.updateById(student);

        log.info("Order created: orderNo={}, id={}", order.getOrderNo(), order.getId());
        return order;
    }

    /**
     * Update order status with state flow validation, tracking insertion,
     * and runner status updates for accept/complete transitions.
     */
    @Transactional
    public void updateStatus(Long orderId, Integer newStatus, Long operatorId, String operatorType) {
        Order order = getById(orderId);
        Integer oldStatus = order.getStatus();

        // Validate the state transition
        StatusFlowValidator.validate(oldStatus, newStatus);

        // When a runner accepts the order (0→1), assign them
        if (newStatus == 1 && "RUNNER".equals(operatorType) && oldStatus == 0) {
            order.setRunnerId(operatorId);
        }

        order.setStatus(newStatus);
        if (newStatus == 5) {
            order.setCompletedAt(LocalDateTime.now());
        }
        orderMapper.updateById(order);

        // Insert tracking record
        OrderTracking tracking = new OrderTracking();
        tracking.setOrderId(orderId);
        tracking.setStatusFrom(oldStatus);
        tracking.setStatusTo(newStatus);
        tracking.setDescription(buildDescription(oldStatus, newStatus));
        tracking.setOperatorId(operatorId);
        tracking.setOperatorType(operatorType);
        orderTrackingMapper.insert(tracking);

        // Update runner state on accept (status=1)
        if (newStatus == 1 && order.getRunnerId() != null) {
            Runner runner = runnerMapper.selectById(order.getRunnerId());
            if (runner != null) {
                runner.setStatus(1);
                runnerMapper.updateById(runner);
                log.info("Runner {} set to busy (accepted order {})", runner.getId(), orderId);
            }
        }

        // Update runner state on complete (status=5)
        if (newStatus == 5 && order.getRunnerId() != null) {
            Runner runner = runnerMapper.selectById(order.getRunnerId());
            if (runner != null) {
                runner.setStatus(0);
                runner.setCompletedOrders(runner.getCompletedOrders() + 1);
                runnerMapper.updateById(runner);
                log.info("Runner {} set to idle, completed_orders incremented (order {})", runner.getId(), orderId);
            }
        }

        log.info("Order {} status changed: {} -> {}", orderId, oldStatus, newStatus);
    }

    /**
     * Build a human-readable description string for the status transition.
     */
    private String buildDescription(Integer oldStatus, Integer newStatus) {
        switch (newStatus) {
            case 1:
                return "跑腿员已接单";
            case 2:
                return "跑腿员前往取件";
            case 3:
                return "已取件配送中";
            case 4:
                return "已送达";
            case 5:
                return "学生确认收货";
            case 6:
                return "订单已取消";
            default:
                return "状态更新";
        }
    }
}
