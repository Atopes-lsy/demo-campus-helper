package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.OrderTracking;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface OrderTrackingMapper extends BaseMapper<OrderTracking> {

    /**
     * Batch-load the first pickup and first delivery time for many orders, for the
     * delivery-time matrix.
     *
     * <p>One query for all orders, replacing a per-order lookup inside a loop (1+N queries).
     * {@code MIN} picks the earliest record per status, matching the previous behaviour of
     * ordering by {@code created_at} ascending and taking the first match.
     */
    @Select("<script>"
            + "SELECT order_id,"
            + " MIN(CASE WHEN status_to = 3 THEN created_at END) AS pickup_time,"
            + " MIN(CASE WHEN status_to = 4 THEN created_at END) AS delivery_time"
            + " FROM order_tracking"
            + " WHERE status_to IN (3, 4)"
            + " AND order_id IN"
            + " <foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY order_id"
            + "</script>")
    List<Map<String, Object>> selectPickupDeliveryTimes(@Param("orderIds") Collection<Long> orderIds);
}
