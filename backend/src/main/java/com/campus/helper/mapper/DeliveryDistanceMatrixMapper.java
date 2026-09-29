package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.DeliveryDistanceMatrix;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface DeliveryDistanceMatrixMapper extends BaseMapper<DeliveryDistanceMatrix> {

    @Select("SELECT pickup_location, delivery_location, avg_minutes, median_minutes, min_minutes, max_minutes, order_count FROM delivery_distance_matrix ORDER BY avg_minutes ASC")
    List<Map<String, Object>> selectMatrixOverview();

    @Select("SELECT * FROM delivery_distance_matrix WHERE pickup_location = #{pickup} AND delivery_location = #{delivery}")
    DeliveryDistanceMatrix selectByRoute(@Param("pickup") String pickup, @Param("delivery") String delivery);

    @Select("SELECT pickup_location, delivery_location, avg_minutes FROM delivery_distance_matrix WHERE pickup_location = #{pickup} ORDER BY avg_minutes ASC")
    List<Map<String, Object>> selectByPickup(@Param("pickup") String pickup);

    /**
     * The fastest known route from a pickup point, as an entity.
     * Lets {@code getETA} fall back to a single query instead of re-reading every
     * candidate route it just listed.
     */
    @Select("SELECT * FROM delivery_distance_matrix WHERE pickup_location = #{pickup} ORDER BY avg_minutes ASC LIMIT 1")
    DeliveryDistanceMatrix selectFirstByPickup(@Param("pickup") String pickup);
}
