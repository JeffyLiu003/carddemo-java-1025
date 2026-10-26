package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.InvalidRequestException;
import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.CampaignStatus;
import com.carddemo.marketing.domain.repository.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 活动用例：真实 H2 + Flyway；每个用例先清空营销三张表，靠测试事务回滚保证隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampaignServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 1);

    @Autowired
    private CampaignService campaigns;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearMarketingData() {
        jdbcTemplate.update("delete from reward_grant");
        jdbcTemplate.update("delete from enrollment");
        jdbcTemplate.update("delete from campaign");
    }

    private Campaign create(String code, String productCode) {
        return campaigns.create(new CreateCampaignCommand(code, "活动 " + code, "活动说明", START_DATE, 3,
                List.of(productCode), new BigDecimal("50.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00"), true));
    }

    @Test
    void createsDraftCampaignWithComputedEndDate() {
        Campaign campaign = create("TEST_PLATINUM_3M", "platinum");

        assertThat(campaign.getCampaignId()).isNotBlank();
        assertThat(campaign.getCode()).isEqualTo("TEST_PLATINUM_3M");
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(campaign.getStartDate()).isEqualTo(START_DATE);
        assertThat(campaign.getDurationMonths()).isEqualTo(3);
        assertThat(campaign.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(campaign.getEligibleProductCodes()).isEqualTo("PLATINUM");
        assertThat(campaign.getMinTransactionAmount()).isEqualByComparingTo("50.00");
        assertThat(campaign.getCashbackRate()).isEqualByComparingTo("0.05");
        assertThat(campaign.getMaxCashbackPerTransaction()).isEqualByComparingTo("30.00");
        assertThat(campaign.getMaxCashbackPerCustomer()).isEqualByComparingTo("300.00");
        assertThat(campaign.getBudget()).isEqualByComparingTo("50000.00");
        assertThat(campaign.getConsumedBudget()).isEqualByComparingTo("0.00");
        assertThat(campaign.budgetRemaining()).isEqualByComparingTo("50000.00");
        assertThat(campaign.isEnrollmentRequired()).isTrue();
        assertThat(campaign.getCreatedAt()).isNotNull();
        assertThat(campaigns.get(campaign.getCampaignId()).getCode()).isEqualTo("TEST_PLATINUM_3M");

        assertThatThrownBy(() -> campaigns.get("missing"))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("CAMPAIGN_NOT_FOUND");
    }

    @Test
    void rejectsDuplicateCode() {
        create("TEST_DUPLICATE", "PLATINUM");

        assertThatThrownBy(() -> create("TEST_DUPLICATE", "PLATINUM"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_CODE_ALREADY_EXISTS");
        assertThatThrownBy(() -> create("test_duplicate", "PLATINUM"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_CODE_ALREADY_EXISTS");
        assertThat(campaigns.list(null, 0, 10).total()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidCampaignDefinition() {
        assertThatThrownBy(() -> campaigns.create(new CreateCampaignCommand("TEST_BAD", "活动", null, START_DATE,
                0, List.of("PLATINUM"), new BigDecimal("50.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00"), true)))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
        assertThatThrownBy(() -> campaigns.create(new CreateCampaignCommand("TEST_BAD", "活动", null, START_DATE,
                3, List.of("PLATINUM"), new BigDecimal("50.00"), new BigDecimal("2.00"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00"), true)))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
        assertThatThrownBy(() -> campaigns.create(new CreateCampaignCommand("TEST_BAD", "活动", null, START_DATE,
                3, List.of("PLATINUM"), new BigDecimal("50.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), BigDecimal.ZERO, true)))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
        assertThatThrownBy(() -> campaigns.create(new CreateCampaignCommand("TEST_BAD", "活动", null, START_DATE,
                3, List.of(), new BigDecimal("50.00"), new BigDecimal("0.05"),
                new BigDecimal("30.00"), new BigDecimal("300.00"), new BigDecimal("50000.00"), true)))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("INVALID_CAMPAIGN");
        assertThat(campaigns.list(null, 0, 10).total()).isZero();
    }

    @Test
    void listsAndFiltersCampaigns() {
        Campaign first = create("TEST_A", "PLATINUM");
        create("TEST_B", "GOLD");
        create("TEST_C", "CLASSIC");
        campaigns.activate(first.getCampaignId());

        PageResult<Campaign> all = campaigns.list(null, 0, 10);
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.items()).hasSize(3);

        PageResult<Campaign> active = campaigns.list("active", 0, 10);
        assertThat(active.total()).isEqualTo(1);
        assertThat(active.items().get(0).getCode()).isEqualTo("TEST_A");

        PageResult<Campaign> paged = campaigns.list(null, 0, 1);
        assertThat(paged.total()).isEqualTo(3);
        assertThat(paged.items()).hasSize(1);

        assertThatThrownBy(() -> campaigns.list("SUSPENDED", 0, 10))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_CAMPAIGN_STATUS");
    }

    @Test
    void activatesPausesAndEndsCampaign() {
        Campaign campaign = create("TEST_LIFECYCLE", "PLATINUM");

        assertThat(campaigns.activate(campaign.getCampaignId()).getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(campaigns.pause(campaign.getCampaignId()).getStatus()).isEqualTo(CampaignStatus.PAUSED);
        assertThat(campaigns.activate(campaign.getCampaignId()).getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(campaigns.end(campaign.getCampaignId()).getStatus()).isEqualTo(CampaignStatus.ENDED);
    }

    @Test
    void rejectsIllegalTransitions() {
        Campaign draft = create("TEST_DRAFT", "PLATINUM");

        assertThatThrownBy(() -> campaigns.pause(draft.getCampaignId()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_INVALID_TRANSITION");
        assertThatThrownBy(() -> campaigns.end(draft.getCampaignId()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_INVALID_TRANSITION");

        campaigns.activate(draft.getCampaignId());
        assertThatThrownBy(() -> campaigns.activate(draft.getCampaignId()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_INVALID_TRANSITION");

        campaigns.end(draft.getCampaignId());
        assertThatThrownBy(() -> campaigns.activate(draft.getCampaignId()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CAMPAIGN_INVALID_TRANSITION");
        assertThatThrownBy(() -> campaigns.end("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("CAMPAIGN_NOT_FOUND");
    }
}
