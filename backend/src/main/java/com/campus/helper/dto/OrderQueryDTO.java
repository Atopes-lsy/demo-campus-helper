package com.campus.helper.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OrderQueryDTO {

    private Integer page = 1;

    private Integer size = 10;

    private Integer orderType;

    private Integer status;

    private String keyword;

    private Long studentId;

    private Long runnerId;

    private LocalDate startDate;

    private LocalDate endDate;
}
