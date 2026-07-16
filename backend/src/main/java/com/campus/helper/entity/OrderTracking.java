package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_tracking")
public class OrderTracking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer statusFrom;
    private Integer statusTo;
    private String description;
    private Long operatorId;
    private String operatorType;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
