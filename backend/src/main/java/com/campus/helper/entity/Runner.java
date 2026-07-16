package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("runner")
public class Runner {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runnerNo;
    private String name;
    private String phone;
    private String idCard;
    private Integer status;
    private BigDecimal rating;
    private Integer completedOrders;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer maxConcurrent;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
