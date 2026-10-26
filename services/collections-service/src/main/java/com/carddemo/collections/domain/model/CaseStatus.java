package com.carddemo.collections.domain.model;

/**
 * 逾期案件状态：OPEN → PTP → RESOLVED；PTP 在承诺日过期后回到 OPEN，RESOLVED 为终态。
 */
public enum CaseStatus {
    OPEN,
    PTP,
    RESOLVED
}
