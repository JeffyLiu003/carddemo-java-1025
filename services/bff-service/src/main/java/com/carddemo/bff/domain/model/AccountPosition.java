package com.carddemo.bff.domain.model;

import java.math.BigDecimal;

/**
 * account-service `GET /api/v1/accounts/{id}/credit-position` 的额度视图。
 */
public record AccountPosition(
        String accountId,
        String productCode,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        BigDecimal activeHoldAmount,
        BigDecimal availableCredit) {
}
