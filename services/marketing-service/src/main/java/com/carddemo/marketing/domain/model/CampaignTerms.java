package com.carddemo.marketing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 活动定义（创建时的一次性输入）。不可变，校验在 {@link Campaign#create} 中完成。
 */
public record CampaignTerms(
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
        boolean enrollmentRequired,
        ReversalPolicy reversalPolicy) {

    /**
     * 未指定追扣口径：口径保持未确认，退款受理返回 409 且不追扣。
     */
    public CampaignTerms(String code, String name, String description, LocalDate startDate, int durationMonths,
                         List<String> eligibleProductCodes, BigDecimal minTransactionAmount, BigDecimal cashbackRate,
                         BigDecimal maxCashbackPerTransaction, BigDecimal maxCashbackPerCustomer,
                         BigDecimal budget, boolean enrollmentRequired) {
        this(code, name, description, startDate, durationMonths, eligibleProductCodes, minTransactionAmount,
                cashbackRate, maxCashbackPerTransaction, maxCashbackPerCustomer, budget, enrollmentRequired, null);
    }
}
