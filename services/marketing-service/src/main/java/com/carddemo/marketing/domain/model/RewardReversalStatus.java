package com.carddemo.marketing.domain.model;

/**
 * 追扣记录状态（设计 §4.3）。本次只实现两个可达状态：
 * PENDING 表示已受理但资金未扣减（含口径未确认），NONE 表示无需追扣。
 */
public enum RewardReversalStatus {
    PENDING,
    NONE
}
