package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.domain.model.AccountingPeriod;

import java.time.LocalDateTime;

public record AccountingPeriodResponse(
        String period,
        String status,
        LocalDateTime closedAt,
        long version) {

    public static AccountingPeriodResponse from(AccountingPeriod period) {
        return new AccountingPeriodResponse(period.getPeriod(), period.getStatus().name(),
                period.getClosedAt(), period.getVersion());
    }
}
