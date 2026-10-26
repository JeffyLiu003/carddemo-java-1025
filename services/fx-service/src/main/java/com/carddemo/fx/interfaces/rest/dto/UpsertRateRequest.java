package com.carddemo.fx.interfaces.rest.dto;

import com.carddemo.fx.application.UpsertRateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率维护（upsert）请求。
 */
public record UpsertRateRequest(
        @Schema(example = "USD")
        @NotBlank(message = "Base currency must be supplied.")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Base currency must be a 3-letter ISO code.")
        String baseCurrency,

        @Schema(example = "CNY")
        @NotBlank(message = "Quote currency must be supplied.")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Quote currency must be a 3-letter ISO code.")
        String quoteCurrency,

        @Schema(example = "7.100000")
        @NotNull(message = "FX rate must be supplied.")
        @DecimalMin(value = "0.000001", message = "FX rate must be greater than zero.")
        BigDecimal rate,

        LocalDateTime effectiveAt) {

    public UpsertRateCommand toCommand() {
        return new UpsertRateCommand(baseCurrency, quoteCurrency, rate, effectiveAt);
    }
}
