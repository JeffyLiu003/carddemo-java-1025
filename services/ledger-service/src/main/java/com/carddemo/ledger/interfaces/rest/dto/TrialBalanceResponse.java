package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.application.TrialBalanceView;

import java.math.BigDecimal;
import java.util.List;

public record TrialBalanceResponse(
        String period,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced,
        List<TrialBalanceItemResponse> items) {

    public static TrialBalanceResponse from(TrialBalanceView view) {
        return new TrialBalanceResponse(
                view.period(),
                view.totalDebit(),
                view.totalCredit(),
                view.balanced(),
                view.items().stream().map(TrialBalanceItemResponse::from).toList());
    }
}
