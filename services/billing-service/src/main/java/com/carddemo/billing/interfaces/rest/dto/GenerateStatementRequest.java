package com.carddemo.billing.interfaces.rest.dto;

import com.carddemo.billing.application.GenerateStatementCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 账单生成请求。
 */
public record GenerateStatementRequest(
        @Schema(description = "账户号", example = "00000000001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Account id must be supplied.")
        String accountId,

        @Schema(description = "账单期间（YYYY-MM）", example = "2026-10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Period must be supplied.")
        String period) {

    public GenerateStatementCommand toCommand() {
        return new GenerateStatementCommand(accountId, period);
    }
}
