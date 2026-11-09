package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.RewardReversal;
import com.carddemo.marketing.domain.model.RewardReversalStatus;
import com.carddemo.marketing.domain.model.ReversalType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追扣记录视图：口径与原因对运营与审计可见。
 */
public record RewardReversalView(
        String reversalId,
        String rewardId,
        String campaignCode,
        ReversalType reversalType,
        BigDecimal reversalAmount,
        BigDecimal clawbackAmount,
        RewardReversalStatus status,
        String reason,
        LocalDateTime createdAt) {

    public static RewardReversalView of(RewardReversal reversal, String campaignCode) {
        return new RewardReversalView(reversal.getReversalId(), reversal.getRewardId(), campaignCode,
                reversal.getReversalType(), reversal.getReversalAmount(), reversal.getClawbackAmount(),
                reversal.getStatus(), reversal.getReason(), reversal.getCreatedAt());
    }
}
