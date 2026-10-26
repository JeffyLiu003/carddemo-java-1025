package com.carddemo.transaction.domain.model;

import java.math.BigDecimal;

/**
 * 过账类型对账户余额的影响方向（契约 §2.2）：INCREASE 记正数，DECREASE 记负数。
 */
public enum AccountEffect {

    INCREASE,
    DECREASE;

    /**
     * 账户侧记账金额：INCREASE 传正数，DECREASE 传负数（契约 §2.3）。
     */
    public BigDecimal apply(BigDecimal amount) {
        return this == INCREASE ? amount : amount.negate();
    }
}
