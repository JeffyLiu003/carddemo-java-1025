package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.CampaignTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建活动用例输入（设计 §4 创建示例）。
 */
public record CreateCampaignCommand(
        String code,
        String name,
        String description,
        LocalDate startDate,
        int durationMonths,
        List<String> eligibleProductCodes,
        BigDecimal minTransactionAmount,
        BigDecimal cashbackRate,
        BigDecimal maxCashbackPerTransaction,
        BigDecimal maxCashbackPerCustomer,
        BigDecimal budget,
        boolean enrollmentRequired) {

    public CampaignTerms toTerms() {
        return new CampaignTerms(code, name, description, startDate, durationMonths, eligibleProductCodes,
                minTransactionAmount, cashbackRate, maxCashbackPerTransaction, maxCashbackPerCustomer,
                budget, enrollmentRequired);
    }
}
