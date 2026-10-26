package com.carddemo.account.interfaces.rest.dto;

import com.carddemo.account.domain.model.AccountHold;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HoldResponse(
        String holdId,
        String accountId,
        BigDecimal amount,
        String status,
        String referenceType,
        String referenceId,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime releasedAt,
        long version) {

    public static HoldResponse from(AccountHold hold) {
        return new HoldResponse(
                hold.getHoldId(),
                hold.getAccountId(),
                hold.getAmount(),
                hold.getStatus().name(),
                hold.getReferenceType(),
                hold.getReferenceId(),
                hold.getCreatedAt(),
                hold.getExpiresAt(),
                hold.getReleasedAt(),
                hold.getVersion());
    }
}
