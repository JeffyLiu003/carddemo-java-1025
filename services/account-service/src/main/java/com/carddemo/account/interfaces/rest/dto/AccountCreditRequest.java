package com.carddemo.account.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 账户贷记请求：creditId 为幂等键，金额必须大于零（余额校验在领域层）。
 */
public record AccountCreditRequest(
        @Schema(description = "贷记幂等键，同一 creditId 只冲减一次", example = "reward-7f1c2d34",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Credit id must be supplied.")
        @Size(max = 36, message = "Credit id must be at most 36 characters.")
        String creditId,

        @Schema(description = "贷记来源类型", example = "CASHBACK",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Source type must be supplied.")
        @Size(max = 30, message = "Source type must be at most 30 characters.")
        String sourceType,

        @Schema(description = "贷记金额（正数）", example = "30.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Amount must be supplied.")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places.")
        BigDecimal amount,

        @Schema(description = "贷记说明", example = "PLATINUM_3M cashback")
        @Size(max = 200, message = "Description must be at most 200 characters.")
        String description) {
}
