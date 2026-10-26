package com.carddemo.loan.domain.model;

/**
 * 贷款申请状态（设计 §2.1）：SUBMITTED → APPROVED / REJECTED → DISBURSED → CLOSED。
 */
public enum LoanApplicationStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    DISBURSED,
    CLOSED
}
