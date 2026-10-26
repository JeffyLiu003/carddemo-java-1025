package com.carddemo.reconciliation.domain.model;

/**
 * 对账类型：业务过账批次 vs 总账日记账、期间试算平衡。
 */
public enum CheckType {
    POSTING_VS_JOURNAL,
    TRIAL_BALANCE
}
