package com.campus.helper.event;

import com.campus.helper.entity.OutboxEvent;
import com.campus.helper.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDeliveryScheduler {

    private final OutboxEventMapper outboxEventMapper;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    @Value("${outbox.max-retries:10}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms:1000}")
    public void deliver() {
        outboxEventMapper.recoverStale(LocalDateTime.now());
        for (OutboxEvent row : outboxEventMapper.selectReady(LocalDateTime.now(), batchSize)) {
            if (outboxEventMapper.claim(row.getId()) != 1) {
                continue;
            }
            deliverOne(row);
        }
    }

    private void deliverOne(OutboxEvent row) {
        try {
            OrderEvent event = objectMapper.readValue(row.getPayload(), OrderEvent.class);
            kafkaTemplate.send(row.getTopic(), row.getEventKey(), event).get(10, TimeUnit.SECONDS);
            outboxEventMapper.markSent(row.getId(), LocalDateTime.now());
            log.debug("Outbox event {} delivered", row.getEventId());
        } catch (Exception e) {
            int retries = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
            String status = retries >= maxRetries ? "DEAD" : "FAILED";
            long delaySeconds = Math.min(300, 1L << Math.min(retries, 8));
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            outboxEventMapper.markFailed(row.getId(), status, retries,
                    LocalDateTime.now().plusSeconds(delaySeconds), message);
            log.error("Outbox event {} delivery failed (retry {}): {}",
                    row.getEventId(), retries, message);
        }
    }
}
