package com.carddemo.account.domain.model;

/**
 * 冻结状态。ACTIVE 占用可用额度，其余状态不再占用。
 */
public enum HoldStatus {
    ACTIVE,
    RELEASED,
    EXPIRED,
    CAPTURED
}
