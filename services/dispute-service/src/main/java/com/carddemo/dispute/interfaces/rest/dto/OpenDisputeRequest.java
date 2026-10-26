package com.carddemo.dispute.interfaces.rest.dto;

import com.carddemo.dispute.application.OpenDisputeCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/v1/disputes 请求体。
 */
public record OpenDisputeRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 36, message = "must be at most 36 characters")
        String transactionId,

        @NotBlank(message = "must not be blank")
        @Size(max = 11, message = "must be at most 11 characters")
        String accountId,

        @NotBlank(message = "must not be blank")
        @Size(max = 20, message = "must be at most 20 characters")
        String customerId,

        @NotBlank(message = "must not be blank")
        @Size(max = 30, message = "must be at most 30 characters")
        String reasonCode,

        @NotNull(message = "must be supplied")
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount) {

    public OpenDisputeCommand toCommand() {
        return new OpenDisputeCommand(transactionId, accountId, customerId, reasonCode, amount);
    }
}
