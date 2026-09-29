package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderMapper extends BaseMapper<Order> {

    @Update("<script>UPDATE order_info SET status=#{newStatus}"
            + "<if test='runnerId != null'>, runner_id=#{runnerId}</if>"
            + "<if test='completedAt != null'>, completed_at=#{completedAt}</if>"
            + " WHERE id=#{orderId} AND status=#{oldStatus}"
            + "<if test='runnerId != null'> AND runner_id IS NULL</if></script>")
    int updateStatusIfCurrent(@Param("orderId") Long orderId,
                              @Param("oldStatus") Integer oldStatus,
                              @Param("newStatus") Integer newStatus,
                              @Param("runnerId") Long runnerId,
                              @Param("completedAt") LocalDateTime completedAt);

    @Select("SELECT student_id, COUNT(*) AS total_orders, SUM(CASE WHEN status=6 THEN 1 ELSE 0 END) AS canceled_orders FROM order_info WHERE created_at >= #{since} GROUP BY student_id HAVING total_orders >= 5")
    List<Map<String, Object>> selectStudentCancelStats(@Param("since") LocalDateTime since);

    /**
     * 每个跑腿员手上进行中的订单数（状态 1/2/3），用于派单负载评分。
     * 一次查出全部跑腿员，替代原先在评分循环里逐个 selectCount（1+N 条 SQL）。
     */
    @Select("SELECT runner_id, COUNT(*) AS active_orders FROM order_info"
            + " WHERE runner_id IS NOT NULL AND status IN (1,2,3) GROUP BY runner_id")
    List<Map<String, Object>> selectActiveOrderCountByRunner();

    /**
     * 每个跑腿员已完成订单的收入合计与单量，用于收入排名。
     * 一次查出全部跑腿员，替代原先在排名循环里逐个 selectList（1+N 条 SQL）。
     * 收入口径与看板一致：订单金额 + 小费。
     */
    @Select("SELECT runner_id,"
            + " COALESCE(SUM(amount), 0) + COALESCE(SUM(tip), 0) AS total_earnings,"
            + " COUNT(*) AS completed_orders"
            + " FROM order_info WHERE runner_id IS NOT NULL AND status = 5 GROUP BY runner_id")
    List<Map<String, Object>> selectEarningsByRunner();

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

    /**
     * Revenue for a specific date range, counted as order amount + tip to match the
     * dashboard and runner-earnings definition of revenue.
     */
    @Select("SELECT COALESCE(SUM(amount), 0) + COALESCE(SUM(tip), 0) AS revenue"
            + " FROM order_info WHERE status = 5 AND completed_at >= #{start} AND completed_at < #{end}")
    java.math.BigDecimal selectRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
