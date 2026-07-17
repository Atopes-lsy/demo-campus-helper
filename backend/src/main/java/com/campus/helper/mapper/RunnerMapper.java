package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.Runner;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface RunnerMapper extends BaseMapper<Runner> {

    @Select("SELECT *, (completed_orders * 0.4 + rating * 0.6) AS score FROM runner ORDER BY score DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopRunners(@Param("limit") int limit);

    /** Runner efficiency stats with average delivery time and completed order count */
    @Select("SELECT r.id, r.name, r.rating, r.completed_orders, r.status, " +
            "AVG(TIMESTAMPDIFF(MINUTE, o.created_at, o.completed_at)) AS avg_minutes " +
            "FROM runner r LEFT JOIN order_info o ON r.id = o.runner_id AND o.status = 5 " +
            "GROUP BY r.id, r.name, r.rating, r.completed_orders, r.status " +
            "ORDER BY r.completed_orders DESC")
    List<Map<String, Object>> selectRunnerEfficiency();
}
