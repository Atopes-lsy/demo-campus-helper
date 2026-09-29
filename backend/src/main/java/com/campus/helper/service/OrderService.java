package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.PageQuery;
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
import com.campus.helper.event.KafkaEventPublisher;
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
    private final PaymentMapper paymentMapper;
    private final KafkaEventPublisher kafkaEventPublisher;
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

        Page<Order> page = PageQuery.of(query.getPage(), query.getSize());
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
    public Order create(OrderCreateDTO dto, Long studentId) {
        Order order = new Order();

        String orderNo = "ORD"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(10000, 100000);
        order.setOrderNo(orderNo);

        // Calculate total amount from package sizes
        BigDecimal totalAmount = BigDecimal.ZERO;
        StringBuilder sizeSummary = new StringBuilder();
        for (PackageItemDTO pkg : dto.getPackages()) {
            BigDecimal price = PACKAGE_PRICE.get(pkg.getSize());
            if (price == null) {
                throw new BusinessException(400, "不支持的包裹尺寸: " + pkg.getSize());
            }
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
        student.setTotalOrders((student.getTotalOrders() == null ? 0 : student.getTotalOrders()) + 1);
        studentMapper.updateById(student);

        log.info("Order created: orderNo={}, id={}", order.getOrderNo(), order.getId());

        // Publish Kafka event (fire-and-forget, does not block)
        kafkaEventPublisher.publish(order, "ORDER_CREATED", null, 0, "学生提交订单");

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

        Runner acceptingRunner = null;
        if (newStatus == 1 && "RUNNER".equals(operatorType) && oldStatus == 0) {
            acceptingRunner = runnerMapper.selectById(operatorId);
            if (acceptingRunner == null || Integer.valueOf(2).equals(acceptingRunner.getStatus())) {
                throw new BusinessException(403, "跑腿员账号不存在或已停用");
            }
        }

        LocalDateTime completedAt = newStatus == 5 ? LocalDateTime.now() : null;
        Long claimedRunnerId = newStatus == 1 && "RUNNER".equals(operatorType) && oldStatus == 0
                ? operatorId : null;
        if (orderMapper.updateStatusIfCurrent(
                orderId, oldStatus, newStatus, claimedRunnerId, completedAt) != 1) {
            throw new BusinessException(409, "订单状态已变化，请刷新后重试");
        }
        order.setStatus(newStatus);
        if (newStatus == 1 && oldStatus == 0) {
            order.setRunnerId(operatorId);
        }
        order.setCompletedAt(completedAt);

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
            Runner runner = acceptingRunner != null ? acceptingRunner : runnerMapper.selectById(order.getRunnerId());
            if (runner != null) {
                runner.setStatus(1);
                runnerMapper.updateById(runner);
                log.info("Runner {} set to busy (accepted order {})", runner.getId(), orderId);
            }
        }

        // Release the runner when an assigned order is completed or cancelled.
        if ((newStatus == 5 || newStatus == 6) && order.getRunnerId() != null) {
            Runner runner = runnerMapper.selectById(order.getRunnerId());
            if (runner != null) {
                runner.setStatus(0);
                if (newStatus == 5) {
                    runner.setCompletedOrders((runner.getCompletedOrders() == null
                            ? 0 : runner.getCompletedOrders()) + 1);
                }
                runnerMapper.updateById(runner);
                log.info("Runner {} released from order {}", runner.getId(), orderId);
            }
        }

        log.info("Order {} status changed: {} -> {}", orderId, oldStatus, newStatus);

        // Publish Kafka event for the status transition (fire-and-forget)
        String eventType = mapEventType(newStatus);
        String description = buildDescription(oldStatus, newStatus);
        kafkaEventPublisher.publish(order, eventType, oldStatus, newStatus, description);
    }

    /**
     * Map order status code to Kafka event type string.
     */
    private String mapEventType(Integer status) {
        return switch (status) {
            case 1 -> "ORDER_ACCEPTED";
            case 2 -> "ORDER_PICKED_UP";
            case 3 -> "ORDER_IN_TRANSIT";
            case 4 -> "ORDER_DELIVERED";
            case 5 -> "ORDER_COMPLETED";
            case 6 -> "ORDER_CANCELLED";
            default -> "ORDER_STATUS_CHANGED";
        };
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
