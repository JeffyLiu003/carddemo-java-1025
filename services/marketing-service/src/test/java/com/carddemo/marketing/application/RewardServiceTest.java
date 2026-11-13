package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.exception.RemoteServiceException;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.RewardGrantStatus;
import com.carddemo.marketing.domain.repository.PageResult;
import com.carddemo.marketing.infrastructure.client.AccountClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 返现评估：命中与各类跳过原因、客户/预算上限、幂等、贷记失败重试、效果与看板口径。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RewardServiceTest {

    private static final String ACCOUNT_ID = "00000000006";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "3344556677889900";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 26, 10, 15);

    @Autowired
    private CampaignService campaigns;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private RewardService rewards;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountClient accountClient;

    @BeforeEach
    void clearMarketingData() {
        jdbcTemplate.update("delete from reward_grant");
        jdbcTemplate.update("delete from enrollment");
        jdbcTemplate.update("delete from campaign");
    }

    private Campaign campaign(String code, String productCode, String startDate, String maxPerCustomer,
                              String budget) {
        Campaign campaign = campaigns.create(new CreateCampaignCommand(code, "活动 " + code, "活动说明",
                LocalDate.parse(startDate), 3, List.of(productCode), new BigDecimal("50.00"),
                new BigDecimal("0.05"), new BigDecimal("30.00"), new BigDecimal(maxPerCustomer),
                new BigDecimal(budget), true));
        return campaigns.activate(campaign.getCampaignId());
    }

    private Campaign platinumCampaign(String budget) {
        return campaign("TEST_PLATINUM", "PLATINUM", "2026-10-01", "300.00", budget);
    }

    private void enroll(Campaign campaign) {
        enrollments.enroll(campaign.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);
    }

    private void stubAccount(String productCode, boolean active) {
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, productCode,
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), active));
    }

    private EvaluationResult evaluate(String transactionId, String amount, LocalDateTime occurredAt) {
        return rewards.evaluate(new EvaluateRewardCommand(transactionId, ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID,
                new BigDecimal(amount), occurredAt));
    }

    @Test
    void grantsCashbackAndReservesBudget() {
        Campaign campaign = platinumCampaign("50000.00");
        stubAccount("PLATINUM", true);
        enroll(campaign);

        EvaluationResult result = evaluate("TXN-1", "1000.00", OCCURRED_AT);

        assertThat(result.skipped()).isEmpty();
        assertThat(result.grants()).hasSize(1);
        EvaluationResult.Grant grant = result.grants().get(0);
        assertThat(grant.campaignCode()).isEqualTo("TEST_PLATINUM");
        assertThat(grant.transactionAmount()).isEqualByComparingTo("1000.00");
        assertThat(grant.cashbackAmount()).isEqualByComparingTo("30.00");
        assertThat(grant.status()).isEqualTo(RewardGrantStatus.GRANTED.name());
        assertThat(grant.grantedAt()).isNotNull();
        assertThat(grant.failureReason()).isNull();

        Campaign reloaded = campaigns.get(campaign.getCampaignId());
        assertThat(reloaded.getConsumedBudget()).isEqualByComparingTo("30.00");
        assertThat(reloaded.budgetRemaining()).isEqualByComparingTo("49970.00");
        verify(accountClient).credit(ACCOUNT_ID, grant.rewardId(), new BigDecimal("30.00"),
                "TEST_PLATINUM cashback");

        PageResult<RewardView> page = rewards.list(ACCOUNT_ID, CUSTOMER_ID, 0, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().get(0).campaignCode()).isEqualTo("TEST_PLATINUM");
        assertThat(page.items().get(0).status()).isEqualTo("GRANTED");
    }

    @Test
    void skipsTransactionBelowMinimumAmount() {
        Campaign campaign = platinumCampaign("50000.00");
        stubAccount("PLATINUM", true);
        enroll(campaign);

        EvaluationResult result = evaluate("TXN-LOW", "40.00", OCCURRED_AT);

        assertThat(result.grants()).isEmpty();
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).isEqualTo("AMOUNT_TOO_LOW");
        assertThat(campaigns.get(campaign.getCampaignId()).getConsumedBudget()).isEqualByComparingTo("0.00");
        verify(accountClient, never()).credit(any(), any(), any(), any());
    }

    @Test
    void skipsCampaignsThatDoNotApply() {
        campaign("TEST_GOLD", "GOLD", "2026-10-01", "300.00", "50000.00");
        campaign("TEST_FUTURE", "PLATINUM", "2027-01-01", "300.00", "50000.00");
        campaign("TEST_NOT_ENROLLED", "PLATINUM", "2026-10-01", "300.00", "50000.00");
        stubAccount("PLATINUM", true);

        EvaluationResult result = evaluate("TXN-SKIP", "1000.00", OCCURRED_AT);

        assertThat(result.grants()).isEmpty();
        assertThat(result.skipped()).hasSize(3);
        assertThat(result.skipped()).extracting(EvaluationResult.Skip::campaignCode)
                .containsExactlyInAnyOrder("TEST_GOLD", "TEST_FUTURE", "TEST_NOT_ENROLLED");
        assertThat(result.skipped()).filteredOn(skip -> skip.campaignCode().equals("TEST_GOLD"))
                .singleElement().extracting(EvaluationResult.Skip::reason).isEqualTo("PRODUCT_NOT_ELIGIBLE");
        assertThat(result.skipped()).filteredOn(skip -> skip.campaignCode().equals("TEST_FUTURE"))
                .singleElement().extracting(EvaluationResult.Skip::reason).isEqualTo("OUTSIDE_WINDOW");
        assertThat(result.skipped()).filteredOn(skip -> skip.campaignCode().equals("TEST_NOT_ENROLLED"))
                .singleElement().extracting(EvaluationResult.Skip::reason).isEqualTo("NOT_ENROLLED");
    }

    @Test
    void evaluationIsIdempotentPerCampaignAndTransaction() {
        Campaign campaign = platinumCampaign("50000.00");
        stubAccount("PLATINUM", true);
        enroll(campaign);

        EvaluationResult first = evaluate("TXN-IDEMPOTENT", "1000.00", OCCURRED_AT);
        EvaluationResult second = evaluate("TXN-IDEMPOTENT", "1000.00", OCCURRED_AT);

        assertThat(first.grants()).hasSize(1);
        assertThat(second.grants()).isEmpty();
        assertThat(second.skipped()).hasSize(1);
        assertThat(second.skipped().get(0).reason()).isEqualTo("ALREADY_EVALUATED");
        assertThat(campaigns.get(campaign.getCampaignId()).getConsumedBudget()).isEqualByComparingTo("30.00");
        verify(accountClient, times(1)).credit(any(), any(), any(), any());
    }

    @Test
    void capsCashbackAtCustomerLimitAndThenSkips() {
        Campaign campaign = campaign("TEST_PLATINUM", "PLATINUM", "2026-10-01", "40.00", "50000.00");
        stubAccount("PLATINUM", true);
        enroll(campaign);

        EvaluationResult first = evaluate("TXN-CAP-1", "1000.00", OCCURRED_AT);
        EvaluationResult second = evaluate("TXN-CAP-2", "1000.00", OCCURRED_AT);
        EvaluationResult third = evaluate("TXN-CAP-3", "1000.00", OCCURRED_AT);

        assertThat(first.grants().get(0).cashbackAmount()).isEqualByComparingTo("30.00");
        assertThat(second.grants().get(0).cashbackAmount()).isEqualByComparingTo("10.00");
        assertThat(third.grants()).isEmpty();
        assertThat(third.skipped().get(0).reason()).isEqualTo("CUSTOMER_CAP_REACHED");
        assertThat(campaigns.get(campaign.getCampaignId()).getConsumedBudget()).isEqualByComparingTo("40.00");
    }

    @Test
    void skipsWhenCampaignBudgetIsExhausted() {
        Campaign campaign = platinumCampaign("30.00");
        stubAccount("PLATINUM", true);
        enroll(campaign);

        EvaluationResult first = evaluate("TXN-BUDGET-1", "1000.00", OCCURRED_AT);
        EvaluationResult second = evaluate("TXN-BUDGET-2", "1000.00", OCCURRED_AT);

        assertThat(first.grants().get(0).cashbackAmount()).isEqualByComparingTo("30.00");
        assertThat(second.grants()).isEmpty();
        assertThat(second.skipped().get(0).reason()).isEqualTo("BUDGET_EXHAUSTED");
        assertThat(campaigns.get(campaign.getCampaignId()).budgetRemaining()).isEqualByComparingTo("0.00");
    }

    @Test
    void keepsPendingWhenCreditFailsAndRetryIsIdempotent() {
        Campaign campaign = platinumCampaign("50000.00");
        stubAccount("PLATINUM", true);
        enroll(campaign);
        doThrow(new RemoteServiceException("account-service is unavailable"))
                .when(accountClient).credit(eq(ACCOUNT_ID), any(), any(), any());

        EvaluationResult result = evaluate("TXN-RETRY", "1000.00", OCCURRED_AT);

        assertThat(result.grants()).hasSize(1);
        EvaluationResult.Grant pending = result.grants().get(0);
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.failureReason()).contains("unavailable");
        assertThat(pending.grantedAt()).isNull();
        assertThat(campaigns.get(campaign.getCampaignId()).getConsumedBudget()).isEqualByComparingTo("30.00");

        doThrow(new RemoteServiceException("account-service is unavailable"))
                .doNothing()
                .when(accountClient).credit(eq(ACCOUNT_ID), any(), any(), any());

        RewardView stillPending = rewards.retry(pending.rewardId());
        assertThat(stillPending.status()).isEqualTo("PENDING");
        assertThat(stillPending.failureReason()).contains("unavailable");

        RewardView granted = rewards.retry(pending.rewardId());
        assertThat(granted.status()).isEqualTo("GRANTED");
        assertThat(granted.grantedAt()).isNotNull();
        assertThat(granted.failureReason()).isNull();

        RewardView repeated = rewards.retry(pending.rewardId());
        assertThat(repeated.status()).isEqualTo("GRANTED");
        assertThat(repeated.grantedAt()).isEqualTo(granted.grantedAt());
    }

    @Test
    void reportsPerformanceAndDashboardSummary() {
        Campaign campaign = platinumCampaign("50000.00");
        stubAccount("PLATINUM", true);
        enroll(campaign);
        evaluate("TXN-PERF", "1000.00", OCCURRED_AT);

        CampaignPerformanceView performance = campaigns.performance(campaign.getCampaignId());

        assertThat(performance.code()).isEqualTo("TEST_PLATINUM");
        assertThat(performance.status()).isEqualTo("ACTIVE");
        assertThat(performance.enrolledCount()).isEqualTo(1);
        assertThat(performance.rewardCount()).isEqualTo(1);
        assertThat(performance.grantedCount()).isEqualTo(1);
        assertThat(performance.pendingCount()).isZero();
        assertThat(performance.cashbackTotal()).isEqualByComparingTo("30.00");
        assertThat(performance.budget()).isEqualByComparingTo("50000.00");
        assertThat(performance.consumedBudget()).isEqualByComparingTo("30.00");
        assertThat(performance.budgetUsageRate()).isEqualByComparingTo("0.0006");
        assertThat(performance.conversionRate()).isEqualByComparingTo("1.0000");

        MarketingSummaryView summary = campaigns.summary();
        assertThat(summary.activeCampaigns()).isEqualTo(1);
        assertThat(summary.grantedRewards()).isEqualTo(1);
        assertThat(summary.cashbackTotal()).isEqualByComparingTo("30.00");
        assertThat(summary.pendingRewards()).isZero();

        assertThatThrownBy(() -> rewards.retry("missing"))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("REWARD_NOT_FOUND");
    }

    @Test
    void propagatesMissingAccountWhenEvaluating() {
        platinumCampaign("50000.00");
        when(accountClient.creditPosition(ACCOUNT_ID))
                .thenThrow(new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + ACCOUNT_ID + " not found"));

        assertThatThrownBy(() -> evaluate("TXN-NO-ACCOUNT", "1000.00", OCCURRED_AT))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
    }

    private Campaign extraHalfPercentCampaign() {
        Campaign campaign = campaigns.create(new CreateCampaignCommand("TEST_EXTRA_HALF_PERCENT",
                "额外 0.5% 返现", "白金卡消费额外 0.5%", LocalDate.parse("2026-11-13"), 3,
                List.of("PLATINUM"), new BigDecimal("50.00"), new BigDecimal("0.005"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00"), false));
        return campaigns.activate(campaign.getCampaignId());
    }

    private static final LocalDateTime EXTRA_WINDOW = LocalDateTime.of(2026, 11, 20, 10, 15);

    @Test
    void stacksPremiumAndExtraHalfPercentCampaigns() {
        Campaign premium = platinumCampaign("50000.00");
        extraHalfPercentCampaign();
        stubAccount("PLATINUM", true);
        enroll(premium);

        EvaluationResult result = rewards.evaluate(new EvaluateRewardCommand("TXN-STACK", ACCOUNT_ID,
                CARD_NUMBER, CUSTOMER_ID, new BigDecimal("1000.00"), EXTRA_WINDOW));

        assertThat(result.skipped()).isEmpty();
        assertThat(result.grants()).hasSize(2);
        assertThat(result.grants()).filteredOn(grant -> grant.campaignCode().equals("TEST_PLATINUM"))
                .singleElement().extracting(EvaluationResult.Grant::cashbackAmount)
                .isEqualTo(new BigDecimal("30.00"));
        assertThat(result.grants()).filteredOn(grant -> grant.campaignCode().equals("TEST_EXTRA_HALF_PERCENT"))
                .singleElement().extracting(EvaluationResult.Grant::cashbackAmount)
                .isEqualTo(new BigDecimal("5.00"));
        assertThat(campaigns.get(premium.getCampaignId()).getConsumedBudget()).isEqualByComparingTo("30.00");
        verify(accountClient, times(2)).credit(any(), any(), any(), any());
    }

    @Test
    void grantsExtraHalfPercentWithinWindowAndCapsSingleTransaction() {
        extraHalfPercentCampaign();
        stubAccount("PLATINUM", true);

        EvaluationResult hundredTwenty = rewards.evaluate(new EvaluateRewardCommand("TXN-120", ACCOUNT_ID,
                CARD_NUMBER, CUSTOMER_ID, new BigDecimal("120.00"), EXTRA_WINDOW));
        assertThat(hundredTwenty.grants()).singleElement()
                .extracting(EvaluationResult.Grant::cashbackAmount).isEqualTo(new BigDecimal("0.60"));

        EvaluationResult sixThousand = rewards.evaluate(new EvaluateRewardCommand("TXN-6000", ACCOUNT_ID,
                CARD_NUMBER, CUSTOMER_ID, new BigDecimal("6000.00"), EXTRA_WINDOW));
        assertThat(sixThousand.grants()).singleElement()
                .extracting(EvaluationResult.Grant::cashbackAmount).isEqualTo(new BigDecimal("30.00"));

        EvaluationResult afterWindow = rewards.evaluate(new EvaluateRewardCommand("TXN-AFTER-WINDOW", ACCOUNT_ID,
                CARD_NUMBER, CUSTOMER_ID, new BigDecimal("120.00"), LocalDateTime.of(2027, 2, 13, 0, 0)));
        assertThat(afterWindow.grants()).isEmpty();
        assertThat(afterWindow.skipped()).singleElement()
                .extracting(EvaluationResult.Skip::reason).isEqualTo("OUTSIDE_WINDOW");
    }
}
