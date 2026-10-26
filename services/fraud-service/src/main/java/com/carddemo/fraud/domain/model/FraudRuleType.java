package com.carddemo.fraud.domain.model;

/**
 * 规则类型。AMOUNT_GT 用阈值，其余用 match_value（逗号分隔表示多个取值）。
 */
public enum FraudRuleType {

    AMOUNT_GT,
    MERCHANT_BLOCKED,
    COUNTRY_BLOCKED
}
