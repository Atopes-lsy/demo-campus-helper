package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.OutboxEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("SELECT * FROM event_outbox WHERE status IN ('PENDING','FAILED') "
            + "AND next_retry_at <= #{now} ORDER BY id LIMIT #{limit}")
    List<OutboxEvent> selectReady(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE event_outbox SET status='SENDING', "
            + "next_retry_at=DATE_ADD(NOW(), INTERVAL 5 MINUTE) WHERE id=#{id} "
            + "AND status IN ('PENDING','FAILED')")
    int claim(@Param("id") Long id);

    @Update("UPDATE event_outbox SET status='SENT', sent_at=#{sentAt}, error_message=NULL "
            + "WHERE id=#{id} AND status='SENDING'")
    int markSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    @Update("UPDATE event_outbox SET status=#{status}, retry_count=#{retryCount}, "
            + "next_retry_at=#{nextRetryAt}, error_message=#{errorMessage} "
            + "WHERE id=#{id} AND status='SENDING'")
    int markFailed(@Param("id") Long id, @Param("status") String status,
                   @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("errorMessage") String errorMessage);

    @Update("UPDATE event_outbox SET status='FAILED', next_retry_at=NOW(), "
            + "error_message='Recovered stale SENDING event' "
            + "WHERE status='SENDING' AND next_retry_at <= #{now}")
    int recoverStale(@Param("now") LocalDateTime now);
}
