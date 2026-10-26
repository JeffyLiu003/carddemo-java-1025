package com.carddemo.fx.interfaces.rest.dto;

import com.carddemo.fx.domain.model.FxQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FxQuoteResponse(
        String quoteId,
        String fromCurrency,
        String toCurrency,
        BigDecimal sourceAmount,
        BigDecimal rate,
        BigDecimal convertedAmount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        boolean consumed) {

    public static FxQuoteResponse from(FxQuote quote) {
        return new FxQuoteResponse(
                quote.getQuoteId(),
                quote.getFromCurrency(),
                quote.getToCurrency(),
                quote.getSourceAmount(),
                quote.getRate(),
                quote.getConvertedAmount(),
                quote.getCreatedAt(),
                quote.getExpiresAt(),
                quote.isConsumed());
    }
}
