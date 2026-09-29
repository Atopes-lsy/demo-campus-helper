package com.campus.helper.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateDTO {

    @NotBlank(message = "Pickup location cannot be blank")
    private String pickupLocation;

    @NotBlank(message = "Delivery location cannot be blank")
    private String deliveryLocation;

    @NotNull(message = "Order type cannot be null")
    @Min(value = 1, message = "Order type must be between 1 and 2")
    @Max(value = 2, message = "Order type must be between 1 and 2")
    private Integer orderType;

    /** Ignored by the server; the student ID comes from the access token. */
    private Long studentId;

    /** Packages in this order — at least one required. Size determines price. */
    @NotEmpty(message = "At least one package is required")
    @Valid
    private List<PackageItemDTO> packages;

    /** Pickup code — only visible to runner after accepting the order */
    private String pickupCode;

    /** Optional tip — defaults to 0 */
    @DecimalMin(value = "0.0", inclusive = true, message = "Tip cannot be negative")
    private BigDecimal tip;
}
