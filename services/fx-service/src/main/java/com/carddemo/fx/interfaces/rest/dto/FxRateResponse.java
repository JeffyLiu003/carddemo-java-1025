package com.carddemo.fx.interfaces.rest.dto;

import com.carddemo.fx.domain.model.FxRate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FxRateResponse(
        String pairId,
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        LocalDateTime effectiveAt,
        boolean active,
        long version) {

    public static FxRateResponse from(FxRate rate) {
        return new FxRateResponse(
                rate.getPairId(),
                rate.getBaseCurrency(),
                rate.getQuoteCurrency(),
                rate.getRate(),
                rate.getEffectiveAt(),
                rate.isActive(),
                rate.getVersion());
    }
}
