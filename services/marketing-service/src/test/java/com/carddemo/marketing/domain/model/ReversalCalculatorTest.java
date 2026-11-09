package com.carddemo.marketing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 追扣判定：未确认口径、明确不追扣、全额与部分、按比例舍入、未发放奖励（设计 §4.2）。
 */
class ReversalCalculatorTest {

    private static final BigDecimal CASHBACK = new BigDecimal("5.00");
    private static final BigDecimal ORIGINAL = new BigDecimal("1000.00");

    private static ReversalDecision decide(ReversalPolicy policy, RewardGrantStatus status, String reversalAmount) {
        return ReversalCalculator.decide(policy, status, CASHBACK, ORIGINAL, new BigDecimal(reversalAmount));
    }

    @Test
    void recordsNoClawbackWhenRewardWasNeverGranted() {
        ReversalDecision decision = decide(ReversalPolicy.FULL, RewardGrantStatus.PENDING, "1000.00");

        assertThat(decision.status()).isEqualTo(RewardReversalStatus.NONE);
        assertThat(decision.clawbackAmount()).isNull();
        assertThat(decision.reason()).isEqualTo(ReversalCalculator.REWARD_NOT_GRANTED);
        assertThat(decision.isUnconfirmed()).isFalse();
    }

    @Test
    void keepsUnconfirmedPolicyPendingWithoutAmount() {
        ReversalDecision decision = decide(null, RewardGrantStatus.GRANTED, "1000.00");

        assertThat(decision.status()).isEqualTo(RewardReversalStatus.PENDING);
        assertThat(decision.clawbackAmount()).isNull();
        assertThat(decision.reason()).isEqualTo(ReversalCalculator.REVERSAL_POLICY_UNCONFIRMED);
        assertThat(decision.isUnconfirmed()).isTrue();
    }

    @Test
    void recordsNoClawbackForNonePolicy() {
        ReversalDecision decision = decide(ReversalPolicy.NONE, RewardGrantStatus.GRANTED, "1000.00");

        assertThat(decision.status()).isEqualTo(RewardReversalStatus.NONE);
        assertThat(decision.clawbackAmount()).isEqualByComparingTo("0.00");
        assertThat(decision.reason()).isEqualTo(ReversalCalculator.NO_CLAWBACK);
    }

    @Test
    void clawsBackWholeCashbackForFullReversal() {
        ReversalDecision decision = decide(ReversalPolicy.FULL, RewardGrantStatus.GRANTED, "1000.00");

        assertThat(decision.status()).isEqualTo(RewardReversalStatus.PENDING);
        assertThat(decision.clawbackAmount()).isEqualByComparingTo("5.00");
        assertThat(decision.reason()).isEqualTo(ReversalCalculator.CLAWBACK_EXECUTION_PENDING);
    }

    @Test
    void treatsReversalAboveOriginalAmountAsFull() {
        ReversalDecision decision = decide(ReversalPolicy.FULL, RewardGrantStatus.GRANTED, "1200.00");

        assertThat(decision.clawbackAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void keepsPartialReversalUnderFullPolicyUnconfirmed() {
        ReversalDecision decision = decide(ReversalPolicy.FULL, RewardGrantStatus.GRANTED, "400.00");

        assertThat(decision.status()).isEqualTo(RewardReversalStatus.PENDING);
        assertThat(decision.clawbackAmount()).isNull();
        assertThat(decision.reason()).isEqualTo(ReversalCalculator.PARTIAL_REVERSAL_POLICY_UNCONFIRMED);
        assertThat(decision.isUnconfirmed()).isTrue();
    }

    @Test
    void proportionalPolicyFollowsReversalShare() {
        ReversalDecision decision = decide(ReversalPolicy.PROPORTIONAL, RewardGrantStatus.GRANTED, "400.00");

        assertThat(decision.status()).isEqualTo(RewardReversalStatus.PENDING);
        assertThat(decision.clawbackAmount()).isEqualByComparingTo("2.00");
        assertThat(decision.reason()).isEqualTo(ReversalCalculator.CLAWBACK_EXECUTION_PENDING);
    }

    @Test
    void proportionalPolicyRoundsHalfUpAndCapsAtCashback() {
        ReversalDecision rounded = decide(ReversalPolicy.PROPORTIONAL, RewardGrantStatus.GRANTED, "333.33");
        ReversalDecision capped = decide(ReversalPolicy.PROPORTIONAL, RewardGrantStatus.GRANTED, "2000.00");

        assertThat(rounded.clawbackAmount()).isEqualByComparingTo("1.67");
        assertThat(capped.clawbackAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void proportionalPolicyNeedsUsableOriginalAmount() {
        ReversalDecision decision = ReversalCalculator.decide(ReversalPolicy.PROPORTIONAL,
                RewardGrantStatus.GRANTED, CASHBACK, BigDecimal.ZERO, new BigDecimal("100.00"));

        assertThat(decision.clawbackAmount()).isNull();
        assertThat(decision.reason()).isEqualTo(ReversalCalculator.PARTIAL_REVERSAL_POLICY_UNCONFIRMED);
    }
}
