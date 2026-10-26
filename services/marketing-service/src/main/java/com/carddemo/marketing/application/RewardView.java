package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.RewardGrant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 返现记录视图：附带活动编码，便于前台直接展示。
 */
public record RewardView(
        String rewardId,
        String campaignId,
        String campaignCode,
        String transactionId,
        String accountId,
        String cardNumber,
        String customerId,
        BigDecimal transactionAmount,
        BigDecimal cashbackAmount,
        String status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime grantedAt) {

    public static RewardView of(RewardGrant grant, String campaignCode) {
        return new RewardView(
                grant.getRewardId(),
                grant.getCampaignId(),
                campaignCode,
                grant.getTransactionId(),
                grant.getAccountId(),
                grant.getCardNumber(),
                grant.getCustomerId(),
                grant.getTransactionAmount(),
                grant.getCashbackAmount(),
                grant.getStatus().name(),
                grant.getFailureReason(),
                grant.getCreatedAt(),
                grant.getGrantedAt());
    }
}
