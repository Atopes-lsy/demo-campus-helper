package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pricing_rule")
public class PricingRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String conditionType;
    private String conditionExpr;
    private BigDecimal priceFactor;
    private String aiReasoning;
    private Integer isActive;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
