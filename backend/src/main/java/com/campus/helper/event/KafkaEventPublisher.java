package com.campus.helper.event;

import com.campus.helper.entity.Order;
import com.campus.helper.entity.OutboxEvent;
import com.campus.helper.mapper.OutboxEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.helper.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writes order lifecycle events to the transactional outbox.
 */
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publish an order status change event.
     *
     * @param order       the order entity after the change
     * @param eventType   e.g. "ORDER_CREATED", "ORDER_DISPATCHED"
     * @param oldStatus   previous status (null for creation)
     * @param newStatus   current status
     * @param description human-readable description
     */
    @Transactional
    public void publish(Order order, String eventType, Integer oldStatus,
                        Integer newStatus, String description) {
        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .studentId(order.getStudentId())
                .runnerId(order.getRunnerId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .title(order.getTitle())
                .amount(order.getAmount())
                .tip(order.getTip())
                .pickupLocation(order.getPickupLocation())
                .deliveryLocation(order.getDeliveryLocation())
                .timestamp(LocalDateTime.now())
                .description(description)
                .build();
        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventId(event.getEventId());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(order.getId());
        outbox.setTopic(KafkaConfig.ORDER_EVENTS_TOPIC);
        outbox.setEventKey(order.getOrderNo());
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(LocalDateTime.now());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "订单事件序列化失败");
        }
        outboxEventMapper.insert(outbox);

        // Ask for delivery once this transaction commits, instead of leaving the row to wait for
        // the next poll tick. See OutboxWakeupListener — best effort, the poll still covers it.
        applicationEventPublisher.publishEvent(new OutboxWakeupEvent());
    }
}
