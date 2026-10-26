package com.carddemo.limit.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 调整额度、占用额度、释放额度的金额请求。
 */
public record AmountRequest(
        @Schema(example = "100.00")
        @NotNull(message = "Amount must be supplied.")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        BigDecimal amount) {
}
