package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.Campaign;

import java.time.LocalDate;

/**
 * 客户可见活动与资格（GET /api/v1/eligibilities）。
 */
public record EligibilityView(
        String campaignId,
        String code,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        int durationMonths,
        boolean eligible,
        boolean enrolled,
        boolean enrollmentRequired,
        String rewardSummary) {

    public static EligibilityView of(Campaign campaign, boolean eligible, boolean enrolled) {
        return new EligibilityView(
                campaign.getCampaignId(),
                campaign.getCode(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getDurationMonths(),
                eligible,
                enrolled,
                campaign.isEnrollmentRequired(),
                campaign.rewardSummary());
    }
}
