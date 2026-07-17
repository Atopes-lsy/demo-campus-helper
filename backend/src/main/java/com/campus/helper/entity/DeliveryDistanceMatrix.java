package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("delivery_distance_matrix")
public class DeliveryDistanceMatrix {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pickupLocation;
    private String deliveryLocation;
    private BigDecimal avgMinutes;
    private BigDecimal medianMinutes;
    private BigDecimal minMinutes;
    private BigDecimal maxMinutes;
    private Integer orderCount;
    private LocalDateTime updatedAt;
}
