package com.carddemo.marketing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 返现计算（PC-07）：cashback = min(金额 × 费率, 单笔上限, 客户剩余额度, 活动剩余预算)，HALF_UP 2 位。
 *
 * <p>结果为 0 表示不该发放（费率为 0 或任一剩余额度已耗尽）。
 */
public final class RewardCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int AMOUNT_SCALE = 2;

    private RewardCalculator() {
    }

    public static BigDecimal cashback(BigDecimal transactionAmount, BigDecimal cashbackRate,
                                      BigDecimal maxCashbackPerTransaction, BigDecimal customerRemaining,
                                      BigDecimal budgetRemaining) {
        if (transactionAmount == null || transactionAmount.signum() <= 0) {
            return ZERO;
        }
        BigDecimal rate = cashbackRate == null ? BigDecimal.ZERO : cashbackRate;
        BigDecimal proportional = transactionAmount.multiply(rate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        return proportional
                .min(amount(maxCashbackPerTransaction))
                .min(amount(customerRemaining))
                .min(amount(budgetRemaining))
                .max(ZERO)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(BigDecimal value) {
        return Objects.requireNonNullElse(value, ZERO).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
