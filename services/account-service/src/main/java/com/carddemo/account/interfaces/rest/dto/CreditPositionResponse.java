package com.carddemo.account.interfaces.rest.dto;

import com.carddemo.account.application.CreditPositionView;

import java.math.BigDecimal;

public record CreditPositionResponse(
        String accountId,
        String productCode,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        BigDecimal activeHoldAmount,
        BigDecimal availableCredit) {

    public static CreditPositionResponse from(CreditPositionView view) {
        return new CreditPositionResponse(
                view.accountId(),
                view.productCode(),
                view.creditLimit(),
                view.currentBalance(),
                view.activeHoldAmount(),
                view.availableCredit());
    }
}
