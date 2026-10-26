package com.carddemo.marketing.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 报名请求。accountId 用于资格校验，customerId 用于客户累计上限，cardNumber 仅作展示。
 */
public record EnrollRequest(
        @Schema(description = "账户号", example = "00000000006", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Account id must be supplied.")
        String accountId,

        @Schema(description = "客户号", example = "000000001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Customer id must be supplied.")
        String customerId,

        @Schema(description = "卡号", example = "3344556677889900")
        String cardNumber) {
}
