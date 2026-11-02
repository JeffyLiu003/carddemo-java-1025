package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.CampaignStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V2 种子活动：与设计 §4 示例一致的期限、产品等级、费率、上限与预算。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketingSeedTest {

    @Autowired
    private CampaignService campaigns;

    @Test
    void seedsTwoActivePremiumCampaigns() {
        Campaign platinum = campaigns.list("ACTIVE", 0, 10).items().stream()
                .filter(campaign -> campaign.getCode().equals("PLATINUM_3M_CASHBACK"))
                .findFirst().orElseThrow();
        Campaign gold = campaigns.list(null, 0, 10).items().stream()
                .filter(campaign -> campaign.getCode().equals("GOLD_6M_CASHBACK"))
                .findFirst().orElseThrow();

        assertThat(platinum.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(platinum.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(platinum.getDurationMonths()).isEqualTo(3);
        assertThat(platinum.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(platinum.productCodes()).containsExactly("PLATINUM");
        assertThat(platinum.getMinTransactionAmount()).isEqualByComparingTo("50.00");
        assertThat(platinum.getCashbackRate()).isEqualByComparingTo("0.05");
        assertThat(platinum.getMaxCashbackPerTransaction()).isEqualByComparingTo("30.00");
        assertThat(platinum.getMaxCashbackPerCustomer()).isEqualByComparingTo("300.00");
        assertThat(platinum.getBudget()).isEqualByComparingTo("50000.00");
        assertThat(platinum.getConsumedBudget()).isEqualByComparingTo("0.00");
        assertThat(platinum.isEnrollmentRequired()).isTrue();

        assertThat(gold.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(gold.getDurationMonths()).isEqualTo(6);
        assertThat(gold.getEndDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(gold.productCodes()).containsExactly("GOLD");
        assertThat(gold.getMinTransactionAmount()).isEqualByComparingTo("30.00");
        assertThat(gold.getCashbackRate()).isEqualByComparingTo("0.03");
        assertThat(gold.getMaxCashbackPerTransaction()).isEqualByComparingTo("20.00");
        assertThat(gold.getMaxCashbackPerCustomer()).isEqualByComparingTo("120.00");
        assertThat(gold.getBudget()).isEqualByComparingTo("20000.00");
        assertThat(gold.isEnrollmentRequired()).isTrue();
    }

    /**
     * V3 额外返现活动：费率 0.005、窗口 2026-11-01 起 3 个月，其余参数沿用既有白金活动。
     */
    @Test
    void seedsPlatinumExtraCashbackCampaign() {
        Campaign extra = campaigns.list(null, 0, 20).items().stream()
                .filter(campaign -> campaign.getCode().equals("PLATINUM_EXTRA_0P5_CASHBACK"))
                .findFirst().orElseThrow();

        assertThat(extra.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(extra.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(extra.getDurationMonths()).isEqualTo(3);
        assertThat(extra.getEndDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(extra.productCodes()).containsExactly("PLATINUM");
        assertThat(extra.getMinTransactionAmount()).isEqualByComparingTo("50.00");
        assertThat(extra.getCashbackRate()).isEqualByComparingTo("0.005");
        assertThat(extra.getMaxCashbackPerTransaction()).isEqualByComparingTo("30.00");
        assertThat(extra.getMaxCashbackPerCustomer()).isEqualByComparingTo("300.00");
        assertThat(extra.getBudget()).isEqualByComparingTo("50000.00");
        assertThat(extra.getConsumedBudget()).isEqualByComparingTo("0.00");
        assertThat(extra.isEnrollmentRequired()).isTrue();
    }
}
