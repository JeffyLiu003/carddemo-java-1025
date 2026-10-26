package com.carddemo.fx.interfaces.rest.dto;

import com.carddemo.fx.application.QuoteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 报价请求。
 */
public record QuoteRequest(
        @Schema(example = "USD")
        @NotBlank(message = "From currency must be supplied.")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "From currency must be a 3-letter ISO code.")
        String fromCurrency,

        @Schema(example = "CNY")
        @NotBlank(message = "To currency must be supplied.")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "To currency must be a 3-letter ISO code.")
        String toCurrency,

        @Schema(example = "100.00")
        @NotNull(message = "Source amount must be supplied.")
        @DecimalMin(value = "0.01", message = "Source amount must be greater than zero.")
        BigDecimal amount) {

    public QuoteCommand toCommand() {
        return new QuoteCommand(fromCurrency, toCurrency, amount);
    }
}
