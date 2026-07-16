package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;
    private Long orderId;
    private Long payerId;
    private Long payeeId;
    private BigDecimal amount;
    private Integer payStatus;
    private String payChannel;
    private String transactionId;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("settled_at")
    private LocalDateTime settledAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
