package com.carddemo.limit.interfaces.rest.dto;

import com.carddemo.limit.domain.model.LimitDefinition;

import java.math.BigDecimal;

public record LimitResponse(
        String limitId,
        String scopeType,
        String scopeId,
        String limitType,
        String currency,
        BigDecimal amount,
        BigDecimal usedAmount,
        BigDecimal remainingAmount,
        boolean active,
        long version) {

    public static LimitResponse from(LimitDefinition definition) {
        return new LimitResponse(
                definition.getLimitId(),
                definition.getScopeType().name(),
                definition.getScopeId(),
                definition.getLimitType().name(),
                definition.getCurrency(),
                definition.getAmount(),
                definition.getUsedAmount(),
                definition.remainingAmount(),
                definition.isActive(),
                definition.getVersion());
    }
}
