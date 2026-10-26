package com.carddemo.marketing.domain.model;

/**
 * 返现评估跳过原因（设计 §4 响应体的 skipped[].reason）。
 */
public enum SkipReason {
    /** 同一活动 + 同一交易已评估过（PC-08）。 */
    ALREADY_EVALUATED,
    /** 活动要求报名但客户未报名（PC-05）。 */
    NOT_ENROLLED,
    /** 账户产品等级不在活动圈选范围内（PC-04）。 */
    PRODUCT_NOT_ELIGIBLE,
    /** 交易日期不在活动起止日期内（PC-03）。 */
    OUTSIDE_WINDOW,
    /** 消费金额低于活动门槛，或按费率算出的返现为 0（PC-06/PC-07）。 */
    AMOUNT_TOO_LOW,
    /** 客户累计返现已达上限（PC-09）。 */
    CUSTOMER_CAP_REACHED,
    /** 活动预算已被 PENDING+GRANTED 返现占满（PC-09）。 */
    BUDGET_EXHAUSTED
}
