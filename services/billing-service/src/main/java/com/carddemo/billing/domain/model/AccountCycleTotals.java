package com.carddemo.billing.domain.model;

import java.math.BigDecimal;

/**
 * 账户周期累计快照（account-service GET /api/v1/accounts/{id}）：
 * currentCycleCredit 为本期消费累计，currentCycleDebit 为负值表示本期还款累计。
 */
public record AccountCycleTotals(
        String accountId,
        String productCode,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit) {
}
