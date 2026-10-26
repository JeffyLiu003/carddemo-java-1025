package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.Enrollment;
import com.carddemo.marketing.domain.model.EnrollmentStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报名与资格：真实 H2 + Flyway，account-service 用 @MockBean 替换。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentServiceTest {

    private static final String ACCOUNT_ID = "00000000006";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "3344556677889900";

    @Autowired
    private CampaignService campaigns;

    @Autowired
    private EnrollmentService enrollments;

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

    private Campaign campaign(String code, String productCode, boolean activeCampaign) {
        Campaign campaign = campaigns.create(new CreateCampaignCommand(code, "活动 " + code, "活动说明",
                LocalDate.of(2026, 10, 1), 3, List.of(productCode), new BigDecimal("50.00"),
                new BigDecimal("0.05"), new BigDecimal("30.00"), new BigDecimal("300.00"),
                new BigDecimal("50000.00"), true));
        return activeCampaign ? campaigns.activate(campaign.getCampaignId()) : campaign;
    }

    private void stubAccount(String accountId, String productCode, boolean activeAccount) {
        when(accountClient.creditPosition(accountId)).thenReturn(new AccountPosition(accountId, productCode,
                new BigDecimal("1200.00"), new BigDecimal("5000.00"), activeAccount));
    }

    @Test
    void enrollsEligibleAccount() {
        Campaign campaign = campaign("TEST_PLATINUM", "PLATINUM", true);
        stubAccount(ACCOUNT_ID, "PLATINUM", true);

        EnrollmentOutcome outcome = enrollments.enroll(campaign.getCampaignId(), ACCOUNT_ID, CARD_NUMBER,
                CUSTOMER_ID);

        assertThat(outcome.created()).isTrue();
        Enrollment enrollment = outcome.enrollment();
        assertThat(enrollment.getEnrollmentId()).isNotBlank();
        assertThat(enrollment.getCampaignId()).isEqualTo(campaign.getCampaignId());
        assertThat(enrollment.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(enrollment.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(enrollment.getCardNumber()).isEqualTo(CARD_NUMBER);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(enrollment.getEnrolledAt()).isNotNull();
        assertThat(enrollments.list(campaign.getCampaignId(), 0, 10).total()).isEqualTo(1);
    }

    @Test
    void repeatedEnrollmentReturnsExistingRecord() {
        Campaign campaign = campaign("TEST_PLATINUM", "PLATINUM", true);
        stubAccount(ACCOUNT_ID, "PLATINUM", true);

        EnrollmentOutcome first = enrollments.enroll(campaign.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);
        EnrollmentOutcome second = enrollments.enroll(campaign.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.enrollment().getEnrollmentId()).isEqualTo(first.enrollment().getEnrollmentId());
        assertThat(enrollments.list(campaign.getCampaignId(), 0, 10).total()).isEqualTo(1);
        verify(accountClient, times(1)).creditPosition(ACCOUNT_ID);
    }

    @Test
    void rejectsEnrollmentWhenCampaignIsNotActive() {
        Campaign draft = campaign("TEST_DRAFT", "PLATINUM", false);
        stubAccount(ACCOUNT_ID, "PLATINUM", true);

        assertThatThrownBy(() -> enrollments.enroll(draft.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_NOT_ACTIVE");

        Campaign paused = campaign("TEST_PAUSED", "PLATINUM", true);
        campaigns.pause(paused.getCampaignId());

        assertThatThrownBy(() -> enrollments.enroll(paused.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_NOT_ACTIVE");
        assertThat(enrollments.list(draft.getCampaignId(), 0, 10).total()).isZero();
    }

    @Test
    void rejectsProductMismatchInactiveAccountAndUnknownAccount() {
        Campaign gold = campaign("TEST_GOLD", "GOLD", true);
        Campaign platinum = campaign("TEST_PLATINUM", "PLATINUM", true);
        stubAccount(ACCOUNT_ID, "PLATINUM", true);

        assertThatThrownBy(() -> enrollments.enroll(gold.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("PRODUCT_NOT_ELIGIBLE");

        stubAccount(ACCOUNT_ID, "PLATINUM", false);
        assertThatThrownBy(() -> enrollments.enroll(platinum.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ACCOUNT_INACTIVE");

        when(accountClient.creditPosition("00000000009"))
                .thenThrow(new NotFoundException("ACCOUNT_NOT_FOUND", "Account 00000000009 not found"));
        assertThatThrownBy(() -> enrollments.enroll(platinum.getCampaignId(), "00000000009", CARD_NUMBER,
                CUSTOMER_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void eligibilitiesListActiveCampaignsWithFlags() {
        Campaign platinum = campaign("TEST_PLATINUM", "PLATINUM", true);
        campaign("TEST_GOLD", "GOLD", true);
        campaign("TEST_DRAFT", "PLATINUM", false);
        stubAccount(ACCOUNT_ID, "PLATINUM", true);
        enrollments.enroll(platinum.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);

        List<EligibilityView> items = enrollments.eligibilities(ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);

        assertThat(items).hasSize(2);
        EligibilityView platinumView = items.stream()
                .filter(item -> item.code().equals("TEST_PLATINUM")).findFirst().orElseThrow();
        EligibilityView goldView = items.stream()
                .filter(item -> item.code().equals("TEST_GOLD")).findFirst().orElseThrow();
        assertThat(platinumView.eligible()).isTrue();
        assertThat(platinumView.enrolled()).isTrue();
        assertThat(platinumView.enrollmentRequired()).isTrue();
        assertThat(platinumView.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(platinumView.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(platinumView.durationMonths()).isEqualTo(3);
        assertThat(platinumView.rewardSummary()).contains("5%");
        assertThat(goldView.eligible()).isFalse();
        assertThat(goldView.enrolled()).isFalse();
        assertThat(items).noneMatch(item -> item.code().equals("TEST_DRAFT"));

        assertThat(enrollments.eligibilities(null, null, null))
                .allMatch(item -> !item.eligible() && !item.enrolled());
    }
}
