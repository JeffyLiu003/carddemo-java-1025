package com.carddemo.marketing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 追扣判定（设计 §4.2，PRD BR-CB-12）。
 *
 * <p>判定顺序固定：未实际发放 → 口径未确认 → 明确不追扣 → 全额追扣 → 部分退款待确认 → 按比例追扣。
 * 这里只计算金额，不移动资金；执行器在口径与借记能力确认后单独接入。
 */
public final class ReversalCalculator {

    public static final String REWARD_NOT_GRANTED = "REWARD_NOT_GRANTED";
    public static final String REVERSAL_POLICY_UNCONFIRMED = "REVERSAL_POLICY_UNCONFIRMED";
    public static final String NO_CLAWBACK = "NO_CLAWBACK";
    public static final String CLAWBACK_EXECUTION_PENDING = "CLAWBACK_EXECUTION_PENDING";
    public static final String PARTIAL_REVERSAL_POLICY_UNCONFIRMED = "PARTIAL_REVERSAL_POLICY_UNCONFIRMED";

    private static final int AMOUNT_SCALE = 2;

    private ReversalCalculator() {
    }

    public static ReversalDecision decide(ReversalPolicy policy, RewardGrantStatus grantStatus,
                                          BigDecimal cashbackAmount, BigDecimal originalAmount,
                                          BigDecimal reversalAmount) {
        if (grantStatus != RewardGrantStatus.GRANTED) {
            return new ReversalDecision(RewardReversalStatus.NONE, null, REWARD_NOT_GRANTED);
        }
        if (policy == null) {
            return new ReversalDecision(RewardReversalStatus.PENDING, null, REVERSAL_POLICY_UNCONFIRMED);
        }
        return switch (policy) {
            case NONE -> new ReversalDecision(RewardReversalStatus.NONE,
                    BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP), NO_CLAWBACK);
            case FULL -> full(cashbackAmount, originalAmount, reversalAmount);
            case PROPORTIONAL -> proportional(cashbackAmount, originalAmount, reversalAmount);
        };
    }

    private static ReversalDecision full(BigDecimal cashbackAmount, BigDecimal originalAmount,
                                         BigDecimal reversalAmount) {
        if (originalAmount == null || reversalAmount.compareTo(originalAmount) < 0) {
            return new ReversalDecision(RewardReversalStatus.PENDING, null,
                    PARTIAL_REVERSAL_POLICY_UNCONFIRMED);
        }
        return new ReversalDecision(RewardReversalStatus.PENDING,
                cashbackAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP), CLAWBACK_EXECUTION_PENDING);
    }

    private static ReversalDecision proportional(BigDecimal cashbackAmount, BigDecimal originalAmount,
                                                 BigDecimal reversalAmount) {
        if (originalAmount == null || originalAmount.signum() <= 0) {
            return new ReversalDecision(RewardReversalStatus.PENDING, null,
                    PARTIAL_REVERSAL_POLICY_UNCONFIRMED);
        }
        BigDecimal share = cashbackAmount.multiply(reversalAmount)
                .divide(originalAmount, AMOUNT_SCALE, RoundingMode.HALF_UP);
        return new ReversalDecision(RewardReversalStatus.PENDING, share.min(cashbackAmount),
                CLAWBACK_EXECUTION_PENDING);
    }
}
