package com.carddemo.marketing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 返现计算（PC-07）：min(金额×费率, 单笔上限, 客户剩余, 预算剩余)，HALF_UP 2 位。
 */
class RewardCalculatorTest {

    @Test
    void capsCashbackAtPerTransactionLimit() {
        assertThat(RewardCalculator.cashback(new BigDecimal("1000.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("30.00");
        assertThat(RewardCalculator.cashback(new BigDecimal("100.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertThat(RewardCalculator.cashback(new BigDecimal("0.30"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("0.02");
        assertThat(RewardCalculator.cashback(new BigDecimal("33.33"), new BigDecimal("0.15"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void capsCashbackAtCustomerAndBudgetRemaining() {
        assertThat(RewardCalculator.cashback(new BigDecimal("1000.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("12.50"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("12.50");
        assertThat(RewardCalculator.cashback(new BigDecimal("1000.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("7.25")))
                .isEqualByComparingTo("7.25");
    }

    @Test
    void returnsZeroWhenNothingCanBeGranted() {
        assertThat(RewardCalculator.cashback(new BigDecimal("1000.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("0.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("0.00");
        assertThat(RewardCalculator.cashback(new BigDecimal("1000.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), null))
                .isEqualByComparingTo("0.00");
        assertThat(RewardCalculator.cashback(new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("0.00");
        assertThat(RewardCalculator.cashback(new BigDecimal("0.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("0.00");
        assertThat(RewardCalculator.cashback(null, new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00")))
                .isEqualByComparingTo("0.00");
    }
}
