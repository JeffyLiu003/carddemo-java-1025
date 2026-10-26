package com.carddemo.reconciliation.domain.model;

import java.math.BigDecimal;

/**
 * 一次对账的汇总口径。日记账数量与借贷合计只统计 sourceType=POSTING_BATCH 的日记账，
 * 手工日记账不参与批次对账（设计 §2）。
 */
public record ReconciliationTotals(
        int batchCount,
        BigDecimal batchAmount,
        int journalCount,
        BigDecimal journalDebitTotal,
        BigDecimal journalCreditTotal,
        int matchCount,
        int mismatchCount,
        int missingJournalCount,
        int missingBatchCount) {

    public boolean matched() {
        return mismatchCount == 0;
    }
}
