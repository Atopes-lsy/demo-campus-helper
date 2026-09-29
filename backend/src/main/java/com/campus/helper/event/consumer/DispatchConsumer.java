package com.campus.helper.event.consumer;

import com.campus.helper.event.EventDeduplicator;
import com.campus.helper.event.KafkaConfig;
import com.campus.helper.event.OrderEvent;
import com.campus.helper.service.DispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triggers automatic dispatch for freshly created orders, closing the loop that
 * {@link DispatchService} was written for but previously had no caller for.
 *
 * <p>Runs in its own consumer group so it sees every event independently, matching the other
 * consumers. Orders it declines — no free runner, or the best score below the threshold — are
 * left at status 0, which is exactly the pool the runner grab path
 * ({@code OrderService.updateStatus}) draws from. Auto-dispatch and grabbing therefore coexist:
 * dispatch gets first refusal, and anything it passes on stays available.
 *
 * <p>The switch and the event type are checked <em>before</em> {@link EventDeduplicator}, and the
 * order matters. Deduplicating first would mark every order created while the switch was off as
 * already consumed, so enabling auto-dispatch later could never pick them up.
 *
 * <p>Exceptions are deliberately not swallowed: the transaction rolls back the idempotency row
 * with them, so Kafka redelivers and a transient failure is retried rather than silently
 * dropping the order out of the dispatch path.
 */
@Slf4j
@Component
public class DispatchConsumer {

    private final DispatchService dispatchService;
    private final EventDeduplicator eventDeduplicator;
    private final boolean autoDispatchEnabled;

    public DispatchConsumer(DispatchService dispatchService,
                            EventDeduplicator eventDeduplicator,
                            @Value("${dispatch.auto-enabled:false}") boolean autoDispatchEnabled) {
        this.dispatchService = dispatchService;
        this.eventDeduplicator = eventDeduplicator;
        this.autoDispatchEnabled = autoDispatchEnabled;
    }

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_TOPIC,
            groupId = "campus-dispatch-group",
            concurrency = "2"
    )
    @Transactional
    public void onOrderEvent(OrderEvent event) {
        if (!autoDispatchEnabled) {
            return;
        }
        if (!"ORDER_CREATED".equals(event.getEventType())) {
            return;
        }
        if (event.getOrderId() == null) {
            log.warn("Skipping dispatch for event {}: no orderId", event.getEventId());
            return;
        }
        if (!eventDeduplicator.firstDelivery(event.getEventId(), "dispatch")) {
            return;
        }

        log.info("[派单消费者] 自动派单: orderId={}", event.getOrderId());
        dispatchService.dispatch(event.getOrderId());
    }
}
