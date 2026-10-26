package com.carddemo.ledger.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * 某个会计期间的试算平衡结果。balanced 为 Σ借方 == Σ贷方。
 */
public record TrialBalanceView(
        String period,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced,
        List<TrialBalanceItem> items) {
}
