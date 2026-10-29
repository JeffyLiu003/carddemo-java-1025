package com.carddemo.marketing.domain.model;

import java.math.BigDecimal;

/**
 * 单个奖励的追扣判定结果（设计 §4.2）：状态、追扣金额（口径未确认时为空）与原因码。
 */
public record ReversalDecision(RewardReversalStatus status, BigDecimal clawbackAmount, String reason) {

    /**
     * 原因码属于“业务口径未确认”，接口返回 409 而不是静默处理。
     */
    public boolean isUnconfirmed() {
        return ReversalCalculator.REVERSAL_POLICY_UNCONFIRMED.equals(reason)
                || ReversalCalculator.PARTIAL_REVERSAL_POLICY_UNCONFIRMED.equals(reason);
    }
}
