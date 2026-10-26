package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.RewardGrant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 返现评估结果（设计 §4 响应）：grants 含已入账与待重试（PENDING）发放，skipped 记录未命中原因。
 */
public record EvaluationResult(List<Grant> grants, List<Skip> skipped) {

    public record Grant(
            String rewardId,
            String campaignId,
            String campaignCode,
            String transactionId,
            String accountId,
            BigDecimal transactionAmount,
            BigDecimal cashbackAmount,
            String status,
            String failureReason,
            LocalDateTime grantedAt) {

        public static Grant of(RewardGrant grant, String campaignCode) {
            return new Grant(
                    grant.getRewardId(),
                    grant.getCampaignId(),
                    campaignCode,
                    grant.getTransactionId(),
                    grant.getAccountId(),
                    grant.getTransactionAmount(),
                    grant.getCashbackAmount(),
                    grant.getStatus().name(),
                    grant.getFailureReason(),
                    grant.getGrantedAt());
        }
    }

    public record Skip(String campaignCode, String reason, String message) {
    }
}
