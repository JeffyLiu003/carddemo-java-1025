package com.carddemo.marketing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 活动效果（GET /api/v1/campaigns/{id}/performance）。
 *
 * <p>口径：cashbackTotal 为已入账（GRANTED）返现；budgetUsageRate = 已预占预算 / 预算（4 位小数）；
 * conversionRate = 报名客户中产生返现的比例（rewardCount / enrolledCount，4 位小数）。
 */
public record CampaignPerformanceView(
        String campaignId,
        String code,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        long enrolledCount,
        long rewardCount,
        long grantedCount,
        long pendingCount,
        BigDecimal cashbackTotal,
        BigDecimal budget,
        BigDecimal consumedBudget,
        BigDecimal budgetRemaining,
        BigDecimal budgetUsageRate,
        BigDecimal conversionRate) {
}
