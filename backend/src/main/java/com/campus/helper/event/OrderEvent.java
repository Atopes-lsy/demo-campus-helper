package com.campus.helper.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order lifecycle event published to Kafka.
 * Covers all state transitions in the order flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    /** Unique event ID (UUID) */
    private String eventId;

    /** Event type enum name: ORDER_CREATED, ORDER_DISPATCHED, etc. */
    private String eventType;

    private Long orderId;
    private String orderNo;

    private Long studentId;
    private Long runnerId;

    /** Previous order status (null for creation) */
    private Integer oldStatus;

    /** New order status */
    private Integer newStatus;

    private String title;
    private BigDecimal amount;
    private BigDecimal tip;

    private String pickupLocation;
    private String deliveryLocation;

    /** ISO-8601 timestamp of when the event occurred */
    private LocalDateTime timestamp;

    /** Human-readable description of the transition, e.g. "跑腿员已接单" */
    private String description;
}
