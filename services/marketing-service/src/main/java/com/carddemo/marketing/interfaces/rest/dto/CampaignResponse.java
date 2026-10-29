package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.domain.model.Campaign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动响应：含计算出的 endDate、已预占预算与剩余预算。
 */
public record CampaignResponse(
        String campaignId,
        String code,
        String name,
        String description,
        String status,
        LocalDate startDate,
        int durationMonths,
        LocalDate endDate,
        List<String> eligibleProductCodes,
        BigDecimal minTransactionAmount,
        BigDecimal cashbackRate,
        BigDecimal maxCashbackPerTransaction,
        BigDecimal maxCashbackPerCustomer,
        BigDecimal budget,
        BigDecimal consumedBudget,
        BigDecimal budgetRemaining,
        boolean enrollmentRequired,
        String reversalPolicy,
        String rewardSummary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long version) {

    public static CampaignResponse from(Campaign campaign) {
        return new CampaignResponse(
                campaign.getCampaignId(),
                campaign.getCode(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getStatus().name(),
                campaign.getStartDate(),
                campaign.getDurationMonths(),
                campaign.getEndDate(),
                campaign.productCodes(),
                campaign.getMinTransactionAmount(),
                campaign.getCashbackRate(),
                campaign.getMaxCashbackPerTransaction(),
                campaign.getMaxCashbackPerCustomer(),
                campaign.getBudget(),
                campaign.getConsumedBudget(),
                campaign.budgetRemaining(),
                campaign.isEnrollmentRequired(),
                campaign.getReversalPolicy() == null ? null : campaign.getReversalPolicy().name(),
                campaign.rewardSummary(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt(),
                campaign.getVersion());
    }
}
