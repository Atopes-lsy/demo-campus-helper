package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT student_id, COUNT(*) AS total_orders, SUM(CASE WHEN status=6 THEN 1 ELSE 0 END) AS canceled_orders FROM order_info WHERE created_at >= #{since} GROUP BY student_id HAVING total_orders >= 5")
    List<Map<String, Object>> selectStudentCancelStats(@Param("since") LocalDateTime since);

    @Select("SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, completed_at)) FROM order_info WHERE runner_id = #{runnerId} AND status = 5")
    Double selectAvgDeliveryMinutes(@Param("runnerId") Long runnerId);

    @Select("SELECT order_type, AVG(tip) AS avg_tip FROM order_info WHERE created_at >= #{since} AND status=5 GROUP BY order_type")
    List<Map<String, Integer>> selectAvgTipByType(@Param("since") LocalDateTime since);

    /** Count today's orders by hour (0-23) */
    @Select("SELECT HOUR(created_at) AS hour, COUNT(*) AS cnt FROM order_info WHERE created_at >= #{since} GROUP BY HOUR(created_at) ORDER BY hour")
    List<Map<String, Object>> selectHourlyOrderStats(@Param("since") LocalDateTime since);

    /** Get completed orders with tracking info for delivery matrix computation */
    @Select("SELECT id, pickup_location, delivery_location FROM order_info WHERE status = 5 AND completed_at >= #{since} AND pickup_location IS NOT NULL AND delivery_location IS NOT NULL")
    List<Map<String, Object>> selectCompletedOrderRoutes(@Param("since") LocalDateTime since);

    /** Today's stats: new orders, completed orders */
    @Select("SELECT COUNT(*) AS total, SUM(CASE WHEN status=5 THEN 1 ELSE 0 END) AS completed FROM order_info WHERE created_at >= #{todayStart}")
    Map<String, Object> selectTodayOrderStats(@Param("todayStart") LocalDateTime todayStart);

    /** Revenue for a specific date range */
    @Select("SELECT COALESCE(SUM(amount), 0) AS revenue FROM order_info WHERE status = 5 AND completed_at >= #{start} AND completed_at < #{end}")
    java.math.BigDecimal selectRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
