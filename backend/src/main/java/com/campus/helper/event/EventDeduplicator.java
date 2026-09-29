package com.campus.helper.event;

import com.campus.helper.mapper.ProcessedEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventDeduplicator {

    private final ProcessedEventMapper processedEventMapper;

    public boolean firstDelivery(String eventId, String consumerName) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        try {
            return processedEventMapper.insert(eventId, consumerName) == 1;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
