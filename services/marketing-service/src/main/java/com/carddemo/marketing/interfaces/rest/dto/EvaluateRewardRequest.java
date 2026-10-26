package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.application.EvaluateRewardCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易返现评估请求（设计 §4 评估示例）。
 */
public record EvaluateRewardRequest(
        @Schema(description = "交易号", example = "0000000000000200", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Transaction id must be supplied.")
        String transactionId,

        @Schema(description = "账户号", example = "00000000006", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Account id must be supplied.")
        String accountId,

        @Schema(description = "客户号", example = "000000001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Customer id must be supplied.")
        String customerId,

        @Schema(description = "卡号", example = "3344556677889900")
        String cardNumber,

        @Schema(description = "交易金额", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Amount must be supplied.")
        BigDecimal amount,

        @Schema(description = "交易时间", example = "2026-10-26T10:15:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Occurred at must be supplied.")
        LocalDateTime occurredAt) {

    public EvaluateRewardCommand toCommand() {
        return new EvaluateRewardCommand(transactionId, accountId, cardNumber, customerId, amount, occurredAt);
    }
}
