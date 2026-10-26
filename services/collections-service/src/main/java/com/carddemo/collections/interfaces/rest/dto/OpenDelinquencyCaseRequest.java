package com.carddemo.collections.interfaces.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 建案请求：{"accountId":"00000000006","daysPastDue":45,"outstandingAmount":1234.56}。
 */
public record OpenDelinquencyCaseRequest(
        @NotBlank(message = "Account id must be supplied.")
        String accountId,

        @NotNull(message = "Days past due must be supplied.")
        @Min(value = 1, message = "Days past due must be at least 1.")
        Integer daysPastDue,

        @NotNull(message = "Outstanding amount must be supplied.")
        @DecimalMin(value = "0.01", message = "Outstanding amount must be greater than zero.")
        BigDecimal outstandingAmount) {
}
