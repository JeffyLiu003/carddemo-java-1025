package com.carddemo.billing.domain.model;

/**
 * 账单状态。OVERDUE 不落库，按 due_date 与当日判定（只读计算）。
 */
public enum StatementStatus {
    GENERATED,
    PAID,
    OVERDUE;

    public boolean isPaid() {
        return this == PAID;
    }
}
