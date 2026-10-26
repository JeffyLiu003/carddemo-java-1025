package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.application.TrialBalanceItem;

import java.math.BigDecimal;

public record TrialBalanceItemResponse(
        String accountCode,
        String accountName,
        String accountType,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal netAmount) {

    public static TrialBalanceItemResponse from(TrialBalanceItem item) {
        return new TrialBalanceItemResponse(item.accountCode(), item.accountName(), item.accountType(),
                item.debitTotal(), item.creditTotal(), item.netAmount());
    }
}
