package com.campus.helper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class PriceEstimate {

    private BigDecimal basePrice;
    private Map<String, BigDecimal> factors;
    private BigDecimal estimatedPrice;
    private Double distanceMeters;
}
