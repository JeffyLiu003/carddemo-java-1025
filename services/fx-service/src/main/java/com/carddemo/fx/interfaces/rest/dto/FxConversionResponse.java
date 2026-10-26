package com.carddemo.fx.interfaces.rest.dto;

import com.carddemo.fx.domain.model.FxConversion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FxConversionResponse(
        String conversionId,
        String quoteId,
        String fromCurrency,
        String toCurrency,
        BigDecimal sourceAmount,
        BigDecimal rate,
        BigDecimal convertedAmount,
        LocalDateTime createdAt) {

    public static FxConversionResponse from(FxConversion conversion) {
        return new FxConversionResponse(
                conversion.getConversionId(),
                conversion.getQuoteId(),
                conversion.getFromCurrency(),
                conversion.getToCurrency(),
                conversion.getSourceAmount(),
                conversion.getRate(),
                conversion.getConvertedAmount(),
                conversion.getCreatedAt());
    }
}
