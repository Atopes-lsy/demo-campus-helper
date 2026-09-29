package com.campus.helper.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCreateDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsUnknownPackageSizeNegativeTipAndUnknownOrderType() {
        PackageItemDTO item = new PackageItemDTO();
        item.setSize("giant");
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setPickupLocation("食堂");
        dto.setDeliveryLocation("宿舍");
        dto.setOrderType(9);
        dto.setPackages(List.of(item));
        dto.setTip(new BigDecimal("-1"));

        var fields = validator.validate(dto).stream()
                .map(v -> v.getPropertyPath().toString()).toList();
        assertTrue(fields.contains("orderType"));
        assertTrue(fields.contains("packages[0].size"));
        assertTrue(fields.contains("tip"));
    }
}
