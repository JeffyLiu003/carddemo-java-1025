package com.carddemo.loan.interfaces.rest.dto;

import com.carddemo.loan.application.CreateLoanApplicationCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * POST /api/v1/loan-applications 请求体。
 */
public record CreateLoanApplicationRequest(
        @NotBlank(message = "must not be blank")
        String customerId,

        @NotBlank(message = "must not be blank")
        String productCode,

        @NotNull(message = "must be supplied")
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal principal,

        @NotNull(message = "must be supplied")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 360, message = "must be at most 360")
        Integer termMonths,

        @NotNull(message = "must be supplied")
        @DecimalMin(value = "0.0000", message = "must not be negative")
        @Digits(integer = 1, fraction = 4, message = "must have at most 4 decimal places")
        BigDecimal annualRate) {

    public CreateLoanApplicationCommand toCommand() {
        return new CreateLoanApplicationCommand(customerId, productCode, principal, termMonths, annualRate);
    }
}
