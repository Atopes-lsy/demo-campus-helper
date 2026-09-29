package com.campus.helper.event.consumer;

import com.campus.helper.event.EventDeduplicator;
import com.campus.helper.event.OrderEvent;
import com.campus.helper.service.DispatchService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the guard order in {@link DispatchConsumer}.
 *
 * <p>The switch and the event type are checked <em>before</em> the idempotency row is written.
 * Deduplicating first would be the subtle bug: every order created while auto-dispatch was off
 * would be recorded as "already consumed", so turning the switch back on could never dispatch
 * them. These tests fail if the guard order is flipped.
 */
class DispatchConsumerTest {

    private static final String EVENT_ID = "evt-1";
    private static final long ORDER_ID = 42L;

    private final DispatchService dispatchService = mock(DispatchService.class);
    private final EventDeduplicator eventDeduplicator = mock(EventDeduplicator.class);

    @Test
    void doesNothingWhenAutoDispatchIsDisabled() {
        DispatchConsumer consumer = new DispatchConsumer(dispatchService, eventDeduplicator, false);

        consumer.onOrderEvent(orderCreated());

        verifyNoInteractions(dispatchService, eventDeduplicator);
    }

    @Test
    void dispatchesOnFirstDeliveryOfAnOrderCreatedEvent() {
        DispatchConsumer consumer = new DispatchConsumer(dispatchService, eventDeduplicator, true);
        when(eventDeduplicator.firstDelivery(EVENT_ID, "dispatch")).thenReturn(true);

        consumer.onOrderEvent(orderCreated());

        verify(dispatchService).dispatch(ORDER_ID);
    }

    @Test
    void ignoresRedeliveryOfTheSameEvent() {
        DispatchConsumer consumer = new DispatchConsumer(dispatchService, eventDeduplicator, true);
        when(eventDeduplicator.firstDelivery(EVENT_ID, "dispatch")).thenReturn(false);

        consumer.onOrderEvent(orderCreated());

        verifyNoInteractions(dispatchService);
    }

    @Test
    void ignoresOtherEventTypesWithoutConsumingTheirIdempotencyRow() {
        DispatchConsumer consumer = new DispatchConsumer(dispatchService, eventDeduplicator, true);
        OrderEvent accepted = orderCreated();
        accepted.setEventType("ORDER_ACCEPTED");

        consumer.onOrderEvent(accepted);

        verifyNoInteractions(dispatchService, eventDeduplicator);
    }

    @Test
    void ignoresEventsCarryingNoOrderId() {
        DispatchConsumer consumer = new DispatchConsumer(dispatchService, eventDeduplicator, true);
        OrderEvent withoutOrder = orderCreated();
        withoutOrder.setOrderId(null);

        consumer.onOrderEvent(withoutOrder);

        verifyNoInteractions(dispatchService, eventDeduplicator);
    }

    private OrderEvent orderCreated() {
        return OrderEvent.builder()
                .eventId(EVENT_ID)
                .eventType("ORDER_CREATED")
                .orderId(ORDER_ID)
                .build();
    }
}
