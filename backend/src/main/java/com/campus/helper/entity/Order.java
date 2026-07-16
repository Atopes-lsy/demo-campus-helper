package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_info")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long studentId;
    private Long runnerId;
    private Integer orderType;
    private String title;
    private String description;
    private String pickupLocation;
    private String deliveryLocation;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private BigDecimal deliveryLat;
    private BigDecimal deliveryLng;
    private BigDecimal amount;
    private BigDecimal tip;

    /** JSON string of package items: [{"size":"small"},{"size":"large"}] */
    private String packages;

    /** Pickup code — hidden from runner until order is accepted */
    private String pickupCode;

    private Integer status;
    private Integer timeoutMinutes;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
