package com.carddemo.loan.domain.model;

/**
 * 贷款状态（设计 §2.1）：审批通过即建账为 ACTIVE（{@code disbursedAt} 为空表示尚未放款），
 * 全部期次结清后 CLOSED。
 */
public enum LoanStatus {
    ACTIVE,
    CLOSED
}
