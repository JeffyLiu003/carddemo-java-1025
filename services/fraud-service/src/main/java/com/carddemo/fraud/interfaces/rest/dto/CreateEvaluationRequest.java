package com.carddemo.fraud.interfaces.rest.dto;

import com.carddemo.fraud.application.EvaluationCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 评分请求（POST /api/v1/fraud-evaluations）。
 */
public record CreateEvaluationRequest(
        @NotBlank @Size(max = 36) String transactionId,
        @NotBlank @Size(max = 11) String accountId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal amount,
        @Size(max = 50) String merchantName,
        @Size(max = 3) String country) {

    public EvaluationCommand toCommand() {
        return new EvaluationCommand(transactionId, accountId, amount, merchantName, country);
    }
}
