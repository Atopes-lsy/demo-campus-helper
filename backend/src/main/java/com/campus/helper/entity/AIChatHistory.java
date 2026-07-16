package com.campus.helper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_history")
public class AIChatHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long userId;
    private String userType;
    private String role;
    private String content;
    private String intent;
    private Integer tokenCount;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
