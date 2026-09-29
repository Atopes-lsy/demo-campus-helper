package com.campus.helper.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Nudges outbox delivery as soon as the writing transaction commits, instead of waiting for the
 * next poll tick. Without this, an event sits in the outbox for up to
 * {@code outbox.publish-interval-ms} before Kafka sees it — which for auto-dispatch means the
 * student's order appears to sit unattended for about a second.
 *
 * <p>This does not weaken the durability story. The outbox is still the only thing that puts an
 * event on Kafka, and the scheduled poll still runs; if the process dies between the commit and
 * this callback, the row is delivered normally on the next tick. The wake-up only removes the
 * wait from the happy path, so it can be best-effort.
 *
 * <p>Calling {@link OutboxDeliveryScheduler#deliver()} concurrently with the scheduled run needs
 * no extra locking: {@code OutboxEventMapper.claim} transitions a row out of PENDING/FAILED
 * conditionally, so only one caller can send any given row.
 *
 * <p>The pass is handed to the shared {@link TaskScheduler} rather than run inline. A deliver
 * pass blocks on a Kafka send for up to ten seconds per row, and the stack this runs on belongs
 * to a request that has already committed — it should not inherit that wait.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWakeupListener {

    private final OutboxDeliveryScheduler outboxDeliveryScheduler;
    private final TaskScheduler taskScheduler;

    /**
     * {@code fallbackExecution = true} so callers that ran without a transaction still get prompt
     * delivery; the scheduled poll covers the rest either way.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOutboxWritten(OutboxWakeupEvent event) {
        try {
            taskScheduler.schedule(outboxDeliveryScheduler::deliver, Instant.now());
        } catch (RuntimeException e) {
            // Runs on the committing thread's stack after the business transaction already
            // committed, so letting this escape would fail a request that actually succeeded.
            log.warn("Could not wake the outbox deliverer; falling back to the poll interval", e);
        }
    }
}
