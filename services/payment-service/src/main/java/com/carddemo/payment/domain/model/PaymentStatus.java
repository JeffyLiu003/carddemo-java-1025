package com.carddemo.payment.domain.model;

/**
 * 支付指令状态机：RECEIVED → SCREENED → ACCEPTED → SETTLED；
 * RETURNED（退汇）与 REJECTED（筛查/受理拒绝）为终态。契约见设计 §2.1。
 */
public enum PaymentStatus {

    RECEIVED,
    SCREENED,
    ACCEPTED,
    SETTLED,
    RETURNED,
    REJECTED;

    public boolean isTerminal() {
        return this == RETURNED || this == REJECTED;
    }
}
