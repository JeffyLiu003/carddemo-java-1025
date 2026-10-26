package com.carddemo.limit.domain.model;

/**
 * 限额类型：消费、取现、日累计、转账。
 */
public enum LimitType {
    PURCHASE,
    CASH,
    DAILY,
    TRANSFER
}
