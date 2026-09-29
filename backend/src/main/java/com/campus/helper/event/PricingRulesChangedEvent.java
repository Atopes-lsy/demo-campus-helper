package com.campus.helper.event;

/**
 * Signals that a pricing rule was written, so cached dispatch weights must be dropped.
 *
 * <p>This is an in-process Spring event rather than a Kafka message on purpose: every
 * instance already shares the same Redis, so a direct eviction takes effect everywhere
 * immediately. Routing it through the outbox would add the poll interval and the consumer
 * lag to the staleness window while buying no correctness — Kafka is the right tool when
 * another system needs to know, which is why the order events use it for the ES index.
 */
public record PricingRulesChangedEvent(Long ruleId) {
}
