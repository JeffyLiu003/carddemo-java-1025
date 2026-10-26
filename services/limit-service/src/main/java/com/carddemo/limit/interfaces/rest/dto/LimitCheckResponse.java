package com.carddemo.limit.interfaces.rest.dto;

import com.carddemo.limit.application.LimitCheckView;

import java.math.BigDecimal;

public record LimitCheckResponse(
        boolean allowed,
        String limitId,
        String scopeType,
        String scopeId,
        String limitType,
        String currency,
        BigDecimal amount,
        BigDecimal usedAmount,
        BigDecimal remainingAmount) {

    public static LimitCheckResponse from(LimitCheckView view) {
        return new LimitCheckResponse(
                view.allowed(),
                view.limitId(),
                view.scopeType().name(),
                view.scopeId(),
                view.limitType().name(),
                view.currency(),
                view.amount(),
                view.usedAmount(),
                view.remainingAmount());
    }
}
