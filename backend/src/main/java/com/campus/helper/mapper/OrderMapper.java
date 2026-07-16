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
}
