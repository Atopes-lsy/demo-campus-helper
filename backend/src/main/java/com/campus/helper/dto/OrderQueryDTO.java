package com.campus.helper.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

@Data
public class OrderQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页最多100条")
    private Integer size = 10;

    private Integer orderType;

    private Integer status;

    private String keyword;

    private Long studentId;

    private Long runnerId;

    private LocalDate startDate;

    private LocalDate endDate;
}
