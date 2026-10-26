package com.carddemo.billing.domain.model;

import java.math.BigDecimal;

/**
 * 账户额度快照（account-service GET /api/v1/accounts/{id}/credit-position）。
 */
public record AccountPosition(
        String accountId,
        String productCode,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        BigDecimal availableCredit) {
}
