package com.carddemo.marketing.application;

import java.math.BigDecimal;

/**
 * 看板汇总（GET /api/v1/marketing/summary）。
 */
public record MarketingSummaryView(
        long activeCampaigns,
        long grantedRewards,
        BigDecimal cashbackTotal,
        long pendingRewards) {
}
