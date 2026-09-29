package com.campus.helper.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderStatusUpdateDTO {

    @NotNull(message = "目标状态不能为空")
    private Integer newStatus;
}
