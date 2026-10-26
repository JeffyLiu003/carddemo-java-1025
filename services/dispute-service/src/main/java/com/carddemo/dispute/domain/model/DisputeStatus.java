package com.carddemo.dispute.domain.model;

/**
 * 争议案件状态（设计 §5.1、§5.2）：
 * OPEN → UNDER_REVIEW → PROVISIONAL_CREDIT → CHARGEBACK → WON / LOST → CLOSED。
 */
public enum DisputeStatus {
    OPEN,
    UNDER_REVIEW,
    PROVISIONAL_CREDIT,
    CHARGEBACK,
    WON,
    LOST,
    CLOSED
}
