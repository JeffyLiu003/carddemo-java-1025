package com.carddemo.limit.interfaces.rest.dto;

import com.carddemo.limit.application.CreateLimitCommand;
import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建限额请求。
 */
public record CreateLimitRequest(
        @Schema(example = "ACCOUNT")
        @NotNull(message = "Limit scope type must be supplied.")
        ScopeType scopeType,

        @Schema(example = "00000000001")
        @NotBlank(message = "Limit scope id must be supplied.")
        @Size(max = 64, message = "Limit scope id must be 64 characters or less.")
        String scopeId,

        @Schema(example = "PURCHASE")
        @NotNull(message = "Limit type must be supplied.")
        LimitType limitType,

        @Schema(example = "USD")
        @NotBlank(message = "Limit currency must be supplied.")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Limit currency must be a 3-letter ISO code.")
        String currency,

        @Schema(example = "1000.00")
        @NotNull(message = "Limit amount must be supplied.")
        @DecimalMin(value = "0.01", message = "Limit amount must be greater than zero.")
        BigDecimal amount,

        Boolean active) {

    public CreateLimitCommand toCommand() {
        return new CreateLimitCommand(scopeType, scopeId, limitType, currency, amount,
                active == null || active);
    }
}
