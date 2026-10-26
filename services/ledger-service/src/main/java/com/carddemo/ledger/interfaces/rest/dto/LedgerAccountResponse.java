package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.domain.model.LedgerAccount;

public record LedgerAccountResponse(
        String accountCode,
        String name,
        String accountType,
        String parentCode,
        String currency,
        boolean active,
        long version) {

    public static LedgerAccountResponse from(LedgerAccount account) {
        return new LedgerAccountResponse(
                account.getAccountCode(),
                account.getName(),
                account.getAccountType().name(),
                account.getParentCode(),
                account.getCurrency(),
                account.isActive(),
                account.getVersion());
    }
}
