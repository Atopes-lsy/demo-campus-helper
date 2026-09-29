package com.campus.helper.event.consumer;

import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderDocument;
import com.campus.helper.event.KafkaConfig;
import com.campus.helper.event.OrderEvent;
import com.campus.helper.event.EventDeduplicator;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.service.OrderSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Consumes order events from Kafka and syncs them to Elasticsearch.
 *
 * This keeps the ES index in sync with MySQL in near-real-time.
 * Every order lifecycle event triggers an upsert into ES.
 *
 * Strategy:
 * <ul>
 *   <li>ORDER_CREATED → fresh index into ES</li>
 *   <li>All status transitions → update the ES document</li>
 *   <li>ORDER_CANCELLED → keep in ES but mark status=6 (for audit trail)</li>
 * </ul>
 *
 * Runs in its own consumer group to receive ALL events independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchSyncConsumer {

    private final OrderSearchService orderSearchService;
    private final OrderMapper orderMapper;
    private final EventDeduplicator eventDeduplicator;

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_TOPIC,
            groupId = "campus-es-sync-group",
            concurrency = "2"
    )
    @Transactional
    public void onOrderEvent(OrderEvent event) {
        if (!eventDeduplicator.firstDelivery(event.getEventId(), "elasticsearch-sync")) {
            return;
        }
        if (event.getOrderId() == null) {
            log.warn("[ES同步] 收到空orderId的事件，跳过");
            return;
        }

        Order order = orderMapper.selectById(event.getOrderId());
        if (order == null) {
            log.warn("[ES同步] 订单 {} 在DB中不存在，跳过ES索引", event.getOrderId());
            return;
        }

        OrderDocument doc = toDocument(order);
        orderSearchService.index(doc);
        log.info("[ES同步] 订单 {} 已同步到ES, status={}, type={}",
                event.getOrderId(), order.getStatus(), event.getEventType());
    }

    /**
     * Convert a MySQL Order entity to an Elasticsearch OrderDocument.
     */
    private OrderDocument toDocument(Order order) {
        return OrderDocument.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .studentId(order.getStudentId())
                .runnerId(order.getRunnerId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .title(order.getTitle())
                .description(order.getDescription())
                .pickupLocation(order.getPickupLocation())
                .deliveryLocation(order.getDeliveryLocation())
                // ES geo_point format: "lat,lon"
                .pickupGeoPoint(order.getPickupLat() != null && order.getPickupLng() != null
                        ? order.getPickupLat() + "," + order.getPickupLng()
                        : null)
                .deliveryGeoPoint(order.getDeliveryLat() != null && order.getDeliveryLng() != null
                        ? order.getDeliveryLat() + "," + order.getDeliveryLng()
                        : null)
                .amount(order.getAmount())
                .tip(order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                .packages(order.getPackages())
                .pickupCode(order.getPickupCode())
                .createdAt(toDateString(order.getCreatedAt()))
                .updatedAt(toDateString(order.getUpdatedAt()))
                .completedAt(toDateString(order.getCompletedAt()))
                .build();
    }

    private String toDateString(java.time.LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
