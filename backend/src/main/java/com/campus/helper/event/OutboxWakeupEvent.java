package com.campus.helper.event;

/**
 * Signals that a transaction wrote at least one row into the outbox, so delivery can start now
 * rather than at the next poll tick.
 *
 * <p>Carries no payload on purpose — the outbox table is the source of truth, and the listener
 * only needs to know that there is something worth delivering. The poll interval remains the
 * fallback, so losing this signal costs latency rather than delivery.
 */
public record OutboxWakeupEvent() {
}
