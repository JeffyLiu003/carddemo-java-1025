package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.domain.model.Campaign;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 已确认口径的回归：白金卡 + 3 个月 + 额外 0.5% + 消费交易（PRD C-01～C-04，US-006 / US-009 / US-010）。
 *
 * <p>这条用例锁定“需求正文已确认”的部分，不涉及活动日期、限额、退款口径等待确认事项。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PremiumBonusCashbackTest {

    private static final String ACCOUNT_ID = "00000000006";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "3344556677889900";

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
        jdbcTemplate.update("delete from reward_reversal");
        jdbcTemplate.update("delete from reward_grant");
        jdbcTemplate.update("delete from enrollment");
        jdbcTemplate.update("delete from campaign");
    }

    private Campaign bonusCampaign(String productCode) {
        Campaign campaign = campaigns.create(new CreateCampaignCommand("TEST_BONUS", "白金卡额外返现", "活动说明",
                LocalDate.parse("2026-10-01"), 3, List.of(productCode), new BigDecimal("0.00"),
                new BigDecimal("0.005"), new BigDecimal("300.00"), new BigDecimal("3000.00"),
                new BigDecimal("50000.00"), true));
        return campaigns.activate(campaign.getCampaignId());
    }

    private void enroll(Campaign campaign) {
        enrollments.enroll(campaign.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);
    }

    private void stubPlatinumAccount() {
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, "PLATINUM",
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), true));
    }

    private EvaluationResult evaluate(String transactionId, String amount, LocalDateTime occurredAt) {
        return rewards.evaluate(new EvaluateRewardCommand(transactionId, ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID,
                new BigDecimal(amount), occurredAt));
    }

    @Test
    void grantsHalfPercentOnTopOfConsumption() {
        Campaign campaign = bonusCampaign("PLATINUM");
        stubPlatinumAccount();
        enroll(campaign);

        EvaluationResult result = evaluate("TXN-BONUS-1", "1000.00", LocalDateTime.of(2026, 10, 26, 10, 15));

        assertThat(result.skipped()).isEmpty();
        assertThat(result.grants()).hasSize(1);
        assertThat(result.grants().get(0).cashbackAmount()).isEqualByComparingTo("5.00");
        assertThat(result.grants().get(0).status()).isEqualTo("GRANTED");
        verify(accountClient).credit(eq(ACCOUNT_ID), any(), eq(new BigDecimal("5.00")), eq("TEST_BONUS cashback"));
    }

    @Test
    void coversTheLastDayOfTheThreeMonthWindowOnly() {
        Campaign campaign = bonusCampaign("PLATINUM");
        stubPlatinumAccount();
        enroll(campaign);

        EvaluationResult lastDay = evaluate("TXN-LAST-DAY", "1000.00", LocalDateTime.of(2026, 12, 31, 23, 59));
        EvaluationResult nextDay = evaluate("TXN-NEXT-DAY", "1000.00", LocalDateTime.of(2027, 1, 1, 0, 0));

        assertThat(campaign.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(lastDay.grants()).hasSize(1);
        assertThat(lastDay.grants().get(0).cashbackAmount()).isEqualByComparingTo("5.00");
        assertThat(nextDay.grants()).isEmpty();
        assertThat(nextDay.skipped().get(0).reason()).isEqualTo("OUTSIDE_WINDOW");
    }

    @Test
    void doesNotGrantTwiceForTheSameTransaction() {
        Campaign campaign = bonusCampaign("PLATINUM");
        stubPlatinumAccount();
        enroll(campaign);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 26, 10, 15);

        evaluate("TXN-BONUS-1", "1000.00", occurredAt);
        EvaluationResult repeated = evaluate("TXN-BONUS-1", "1000.00", occurredAt);

        assertThat(repeated.grants()).isEmpty();
        assertThat(repeated.skipped().get(0).reason()).isEqualTo("ALREADY_EVALUATED");
        verify(accountClient, times(1)).credit(any(), any(), any(), any());
    }

    @Test
    void doesNotGrantForNonPlatinumAccounts() {
        Campaign campaign = bonusCampaign("PLATINUM");
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, "GOLD",
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), true));

        EvaluationResult result = evaluate("TXN-GOLD", "1000.00", LocalDateTime.of(2026, 10, 26, 10, 15));

        assertThat(result.grants()).isEmpty();
        assertThat(result.skipped().get(0).reason()).isEqualTo("PRODUCT_NOT_ELIGIBLE");
        verify(accountClient, times(0)).credit(any(), any(), any(), any());
    }
}
