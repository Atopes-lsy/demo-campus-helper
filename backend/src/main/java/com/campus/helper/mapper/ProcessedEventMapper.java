package com.campus.helper.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ProcessedEventMapper {

    @Insert("INSERT INTO processed_event(event_id, consumer_name) VALUES(#{eventId}, #{consumerName})")
    int insert(@Param("eventId") String eventId, @Param("consumerName") String consumerName);
}
