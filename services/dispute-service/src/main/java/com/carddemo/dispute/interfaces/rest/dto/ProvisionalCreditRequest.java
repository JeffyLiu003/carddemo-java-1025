package com.carddemo.dispute.interfaces.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * POST /api/v1/disputes/{id}/provisional-credit 请求体。
 */
public record ProvisionalCreditRequest(
        @NotNull(message = "must be supplied")
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount) {
}
