package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.InvalidRequestException;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.ReversalPolicy;
import com.carddemo.marketing.domain.model.ReversalType;
import com.carddemo.marketing.domain.model.RewardReversalStatus;
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
import static org.mockito.Mockito.when;

/**
 * 退款与冲正受理：无奖励交易、口径未确认、明确不追扣、全额与部分、按比例、幂等（设计 §9）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RewardReversalServiceTest {

    private static final String ACCOUNT_ID = "00000000006";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "3344556677889900";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 26, 10, 15);
    private static final String ORIGINAL_TXN = "TXN-BONUS-1";

    @Autowired
    private CampaignService campaigns;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private RewardService rewards;

    @Autowired
    private RewardReversalService reversals;

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

    /**
     * 白金卡额外 0.5% 活动：1000.00 消费返 5.00，并按给定口径配置追扣规则。
     */
    private void grantedBonusCampaign(ReversalPolicy policy) {
        CreateCampaignCommand command = new CreateCampaignCommand("TEST_BONUS", "白金卡额外返现", "活动说明",
                LocalDate.parse("2026-10-01"), 3, List.of("PLATINUM"), new BigDecimal("0.00"),
                new BigDecimal("0.005"), new BigDecimal("300.00"), new BigDecimal("3000.00"),
                new BigDecimal("50000.00"), true, policy);
        Campaign campaign = campaigns.create(command);
        campaigns.activate(campaign.getCampaignId());
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, "PLATINUM",
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), true));
        enrollments.enroll(campaign.getCampaignId(), ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);
        rewards.evaluate(new EvaluateRewardCommand(ORIGINAL_TXN, ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID,
                new BigDecimal("1000.00"), OCCURRED_AT));
    }

    private RewardReversalListView reverse(String reversalTransactionId, ReversalType type, String amount) {
        return reversals.accept(new ReverseRewardCommand(reversalTransactionId, ORIGINAL_TXN, type,
                new BigDecimal(amount)));
    }

    private int reversalCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from reward_reversal", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    void returnsEmptyResultWhenOriginalTransactionHasNoReward() {
        RewardReversalListView view = reversals.accept(new ReverseRewardCommand("REV-NONE", "TXN-UNKNOWN",
                ReversalType.REFUND, new BigDecimal("100.00")));

        assertThat(view.reversals()).isEmpty();
        assertThat(reversalCount()).isZero();
    }

    @Test
    void recordsPendingClawbackForConfirmedFullPolicy() {
        grantedBonusCampaign(ReversalPolicy.FULL);

        RewardReversalListView view = reverse("REV-FULL", ReversalType.REFUND, "1000.00");

        assertThat(view.reversals()).hasSize(1);
        RewardReversalView reversal = view.reversals().get(0);
        assertThat(reversal.campaignCode()).isEqualTo("TEST_BONUS");
        assertThat(reversal.reversalType()).isEqualTo(ReversalType.REFUND);
        assertThat(reversal.reversalAmount()).isEqualByComparingTo("1000.00");
        assertThat(reversal.clawbackAmount()).isEqualByComparingTo("5.00");
        assertThat(reversal.status()).isEqualTo(RewardReversalStatus.PENDING);
        assertThat(reversal.reason()).isEqualTo("CLAWBACK_EXECUTION_PENDING");
    }

    @Test
    void keepsRecordAndRejectsWhenPolicyIsUnconfirmed() {
        grantedBonusCampaign(null);

        assertThatThrownBy(() -> reverse("REV-UNCONFIRMED", ReversalType.REFUND, "1000.00"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("REVERSAL_POLICY_UNCONFIRMED");
        assertThat(reversalCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from reward_reversal", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void recordsNoClawbackForNonePolicy() {
        grantedBonusCampaign(ReversalPolicy.NONE);

        RewardReversalListView view = reverse("REV-NONE-POLICY", ReversalType.CHARGEBACK, "1000.00");

        RewardReversalView reversal = view.reversals().get(0);
        assertThat(reversal.status()).isEqualTo(RewardReversalStatus.NONE);
        assertThat(reversal.clawbackAmount()).isEqualByComparingTo("0.00");
        assertThat(reversal.reason()).isEqualTo("NO_CLAWBACK");
    }

    @Test
    void rejectsPartialRefundUnderFullPolicy() {
        grantedBonusCampaign(ReversalPolicy.FULL);

        assertThatThrownBy(() -> reverse("REV-PARTIAL", ReversalType.REFUND, "400.00"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("PARTIAL_REVERSAL_POLICY_UNCONFIRMED");
        assertThat(reversalCount()).isEqualTo(1);
    }

    @Test
    void computesProportionalClawback() {
        grantedBonusCampaign(ReversalPolicy.PROPORTIONAL);

        RewardReversalListView view = reverse("REV-PROPORTIONAL", ReversalType.REFUND, "400.00");

        assertThat(view.reversals().get(0).clawbackAmount()).isEqualByComparingTo("2.00");
    }

    @Test
    void repeatedReversalRequestIsIdempotent() {
        grantedBonusCampaign(ReversalPolicy.FULL);

        RewardReversalView first = reverse("REV-DUP", ReversalType.REFUND, "1000.00").reversals().get(0);
        RewardReversalView second = reverse("REV-DUP", ReversalType.REFUND, "1000.00").reversals().get(0);

        assertThat(second.reversalId()).isEqualTo(first.reversalId());
        assertThat(reversalCount()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveAmountAndUnknownOriginalTransactionId() {
        grantedBonusCampaign(ReversalPolicy.FULL);

        assertThatThrownBy(() -> reverse("REV-ZERO", ReversalType.REFUND, "0.00"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_AMOUNT");
        assertThatThrownBy(() -> reversals.accept(new ReverseRewardCommand(" ", ORIGINAL_TXN,
                ReversalType.REFUND, new BigDecimal("1000.00"))))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_REVERSAL_TRANSACTION_ID");
    }
}
