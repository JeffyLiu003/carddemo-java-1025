package com.carddemo.ledger.application;

import java.math.BigDecimal;

/**
 * 试算平衡的科目行：借方合计、贷方合计与净额（借 - 贷）。
 */
public record TrialBalanceItem(
        String accountCode,
        String accountName,
        String accountType,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal netAmount) {
}
