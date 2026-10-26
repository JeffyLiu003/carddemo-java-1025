package com.carddemo.account.domain.model;

/**
 * 账户产品类别。当前系统以信用卡为主，保留借记/储蓄以支持后续扩展。
 */
public enum ProductCategory {
    CREDIT,
    DEBIT,
    SAVINGS
}
