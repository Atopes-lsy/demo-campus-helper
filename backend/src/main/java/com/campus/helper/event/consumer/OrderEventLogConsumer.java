package com.campus.helper.event.consumer;

import com.campus.helper.event.KafkaConfig;
import com.campus.helper.event.OrderEvent;
import com.campus.helper.event.EventDeduplicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit-log consumer — records every order lifecycle event as a structured log.
 * In production this could write to Elasticsearch or a dedicated audit table.
 *
 * Runs in its own consumer group to receive ALL events independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventLogConsumer {

    private final EventDeduplicator eventDeduplicator;

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_TOPIC,
            groupId = "campus-audit-log-group",
            concurrency = "1"
    )
    @Transactional
    public void onOrderEvent(OrderEvent event) {
        if (!eventDeduplicator.firstDelivery(event.getEventId(), "audit-log")) {
            return;
        }
        // Structured audit log — easily parsable by log aggregation tools (ELK, Loki, etc.)
        log.info("AUDIT | eventId={} | type={} | orderNo={} | orderId={} | "
                        + "studentId={} | runnerId={} | oldStatus={} | newStatus={} | "
                        + "amount={} | tip={} | pickup={} | delivery={} | desc={} | timestamp={}",
                event.getEventId(),
                event.getEventType(),
                event.getOrderNo(),
                event.getOrderId(),
                event.getStudentId(),
                event.getRunnerId(),
                event.getOldStatus(),
                event.getNewStatus(),
                event.getAmount(),
                event.getTip(),
                event.getPickupLocation(),
                event.getDeliveryLocation(),
                event.getDescription(),
                event.getTimestamp()
        );
    }
}
