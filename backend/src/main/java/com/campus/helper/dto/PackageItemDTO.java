package com.campus.helper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Single package item within an order.
 * Each package has a size that determines its price.
 */
@Data
public class PackageItemDTO {

    /** Package size: small (20×20, ¥4), medium (40×40, ¥5), large (60×60, ¥8) */
    @NotBlank(message = "Package size cannot be blank")
    @Pattern(regexp = "small|medium|large", message = "Package size must be small, medium, or large")
    private String size;
}
