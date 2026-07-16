package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("anomaly_alert")
public class AnomalyAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;
    private Long targetId;
    private String anomalyType;
    private Integer severity;
    private String description;
    private String aiAnalysis;
    private Integer isResolved;

    @TableField("detected_at")
    private LocalDateTime detectedAt;
}
