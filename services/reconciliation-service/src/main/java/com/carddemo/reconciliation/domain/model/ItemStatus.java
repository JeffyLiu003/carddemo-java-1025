package com.carddemo.reconciliation.domain.model;

/**
 * 对账明细结论。MATCH 之外的取值都是差异项，需要登记处理说明。
 */
public enum ItemStatus {
    MATCH,
    MISSING_JOURNAL,
    MISSING_BATCH,
    AMOUNT_MISMATCH,
    UNBALANCED
}
