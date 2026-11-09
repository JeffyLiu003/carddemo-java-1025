package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.application.ReverseRewardCommand;
import com.carddemo.marketing.domain.model.ReversalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 退款与冲正受理请求（设计 §5.1）。
 */
public record ReverseRewardRequest(
        @Schema(description = "退款或冲正交易号（幂等键）", example = "0000000000000300",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Reversal transaction id must be supplied.")
        String reversalTransactionId,

        @Schema(description = "原消费交易号", example = "0000000000000200",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Original transaction id must be supplied.")
        String originalTransactionId,

        @Schema(description = "反向交易类型", example = "REFUND",
                allowableValues = {"REFUND", "CHARGEBACK"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Reversal type must be supplied.")
        String reversalType,

        @Schema(description = "退款或冲正金额", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Reversal amount must be supplied.")
        BigDecimal reversalAmount) {

    public ReverseRewardCommand toCommand() {
        return new ReverseRewardCommand(reversalTransactionId, originalTransactionId,
                ReversalType.parse(reversalType), reversalAmount);
    }
}
