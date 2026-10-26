package com.carddemo.marketing.domain.model;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 活动领域：期限计算（PC-01）、参数边界（PC-02）、状态迁移、产品/窗口圈选、预算预占（PC-09）。
 */
class CampaignTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 9, 0);

    private static CampaignTerms terms(int durationMonths, List<String> productCodes, String startDate,
                                       String rate, String maxPerTransaction, String maxPerCustomer,
                                       String budget) {
        return new CampaignTerms("PLATINUM_3M_CASHBACK", "白金卡 3 个月消费返现", "活动说明",
                LocalDate.parse(startDate), durationMonths, productCodes, new BigDecimal("50.00"),
                new BigDecimal(rate), new BigDecimal(maxPerTransaction), new BigDecimal(maxPerCustomer),
                new BigDecimal(budget), true);
    }

    private static Campaign campaign(int durationMonths) {
        return Campaign.create("C-1", terms(durationMonths, List.of("PLATINUM"), "2026-10-01", "0.05",
                "30.00", "300.00", "50000.00"), CREATED_AT);
    }

    @Test
    void calculatesEndDateFromDurationMonths() {
        Campaign oneMonth = campaign(1);
        Campaign threeMonths = campaign(3);
        Campaign sixMonths = campaign(6);
        Campaign twentyFourMonths = campaign(24);

        assertThat(oneMonth.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(oneMonth.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(threeMonths.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(sixMonths.getEndDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(twentyFourMonths.getEndDate()).isEqualTo(LocalDate.of(2028, 9, 30));
        assertThat(threeMonths.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(threeMonths.getConsumedBudget()).isEqualByComparingTo("0.00");
        assertThat(threeMonths.budgetRemaining()).isEqualByComparingTo("50000.00");
        assertThat(threeMonths.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(threeMonths.rewardSummary()).contains("5%").contains("30.00").contains("300.00");
    }

    @Test
    void rejectsInvalidDurationRateLimitsAndBudget() {
        assertThatThrownBy(() -> campaign(0))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
        assertThatThrownBy(() -> campaign(25))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
        assertThatThrownBy(() -> Campaign.create("C-1", terms(3, List.of("PLATINUM"), "2026-10-01", "1.5",
                "30.00", "300.00", "50000.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Campaign.create("C-1", terms(3, List.of("PLATINUM"), "2026-10-01", "-0.1",
                "30.00", "300.00", "50000.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Campaign.create("C-1", terms(3, List.of("PLATINUM"), "2026-10-01", "0.05",
                "0.00", "300.00", "50000.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Campaign.create("C-1", terms(3, List.of("PLATINUM"), "2026-10-01", "0.05",
                "30.00", "0.00", "50000.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Campaign.create("C-1", terms(3, List.of("PLATINUM"), "2026-10-01", "0.05",
                "30.00", "300.00", "0.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Campaign.create("C-1", terms(3, List.of(), "2026-10-01", "0.05",
                "30.00", "300.00", "50000.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Campaign.create("C-1", terms(3, List.of(" "), "2026-10-01", "0.05",
                "30.00", "300.00", "50000.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Campaign.create(" ", terms(3, List.of("PLATINUM"), "2026-10-01", "0.05",
                "30.00", "300.00", "50000.00"), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
        assertThatThrownBy(() -> Campaign.create("C-1", new CampaignTerms("PLATINUM_3M_CASHBACK", " ",
                "活动说明", LocalDate.of(2026, 10, 1), 3, List.of("PLATINUM"), new BigDecimal("50.00"),
                new BigDecimal("0.05"), new BigDecimal("30.00"), new BigDecimal("300.00"),
                new BigDecimal("50000.00"), true), CREATED_AT))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
    }

    @Test
    void transitionsFollowTheLifecycleAndRejectIllegalMoves() {
        Campaign campaign = campaign(3);
        LocalDateTime now = CREATED_AT.plusDays(1);

        assertThatThrownBy(() -> campaign.pause(now))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_INVALID_TRANSITION");
        assertThatThrownBy(() -> campaign.end(now))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_INVALID_TRANSITION");
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.DRAFT);

        campaign.activate(now);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(campaign.getUpdatedAt()).isEqualTo(now);
        assertThatThrownBy(() -> campaign.activate(now))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_INVALID_TRANSITION");

        campaign.pause(now.plusHours(1));
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PAUSED);
        campaign.activate(now.plusHours(2));
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);

        campaign.end(now.plusHours(3));
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ENDED);
        assertThatThrownBy(() -> campaign.activate(now.plusHours(4)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> campaign.end(now.plusHours(4)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void matchesProductCodesAndTransactionWindow() {
        Campaign campaign = Campaign.create("C-1", terms(3, List.of(" platinum ", "GOLD"), "2026-10-01",
                "0.05", "30.00", "300.00", "50000.00"), CREATED_AT);

        assertThat(campaign.getEligibleProductCodes()).isEqualTo("PLATINUM,GOLD");
        assertThat(campaign.productCodes()).containsExactly("PLATINUM", "GOLD");
        assertThat(campaign.isProductEligible("platinum")).isTrue();
        assertThat(campaign.isProductEligible("PLATINUM")).isTrue();
        assertThat(campaign.isProductEligible("CLASSIC")).isFalse();
        assertThat(campaign.isProductEligible(null)).isFalse();

        assertThat(campaign.isWithinWindow(LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(campaign.isWithinWindow(LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(campaign.isWithinWindow(LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(campaign.isWithinWindow(LocalDate.of(2027, 1, 1))).isFalse();
        assertThat(campaign.isWithinWindow(null)).isFalse();
    }

    @Test
    void reservesBudgetWithoutExceedingIt() {
        Campaign campaign = Campaign.create("C-1", terms(3, List.of("PLATINUM"), "2026-10-01", "0.05",
                "30.00", "300.00", "100.00"), CREATED_AT);
        LocalDateTime now = CREATED_AT.plusDays(1);

        campaign.reserveBudget(new BigDecimal("30.00"), now);
        assertThat(campaign.getConsumedBudget()).isEqualByComparingTo("30.00");
        assertThat(campaign.budgetRemaining()).isEqualByComparingTo("70.00");

        campaign.reserveBudget(new BigDecimal("70.00"), now);
        assertThat(campaign.budgetRemaining()).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> campaign.reserveBudget(new BigDecimal("1.00"), now))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_BUDGET_EXHAUSTED");
        assertThat(campaign.budgetRemaining()).isEqualByComparingTo("0.00");
    }
}
