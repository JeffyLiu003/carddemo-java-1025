package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.RewardGrantStatus;
import com.carddemo.marketing.domain.repository.CampaignRepository;
import com.carddemo.marketing.infrastructure.client.AccountClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 白金卡额外 0.5% 返现活动种子（V3）：活动参数、窗口边界、卡等级与最低消费口径。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatinumExtraCashbackSeedTest {

    private static final String CODE = "PLATINUM_EXTRA_05";
    private static final String ACCOUNT_ID = "00000000006";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "3344556677889900";

    @Autowired
    private CampaignRepository campaigns;

    @Autowired
    private RewardService rewards;

    @MockBean
    private AccountClient accountClient;

    private Campaign seeded() {
        return campaigns.findByCode(CODE).orElseThrow(() -> new AssertionError(CODE + " is not seeded"));
    }

    private EvaluationResult evaluate(String transactionId, String amount, LocalDateTime occurredAt,
                                      String productCode) {
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, productCode,
                new BigDecimal("2550.75"), new BigDecimal("22449.25"), true));
        return rewards.evaluate(new EvaluateRewardCommand(transactionId, ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID,
                new BigDecimal(amount), occurredAt));
    }

    private EvaluationResult evaluatePlatinum(String transactionId, String amount, LocalDateTime occurredAt) {
        return evaluate(transactionId, amount, occurredAt, "PLATINUM");
    }

    private static List<EvaluationResult.Grant> grantsOf(EvaluationResult result) {
        return result.grants().stream().filter(grant -> CODE.equals(grant.campaignCode())).toList();
    }

    private static List<String> skipReasonsOf(EvaluationResult result) {
        return result.skipped().stream().filter(skip -> CODE.equals(skip.campaignCode()))
                .map(EvaluationResult.Skip::reason).toList();
    }

    @Test
    void seedsActiveThreeMonthPlatinumCampaignWithoutEnrollment() {
        Campaign campaign = seeded();

        assertThat(campaign.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(campaign.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(campaign.getDurationMonths()).isEqualTo(3);
        assertThat(campaign.getEndDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(campaign.getEligibleProductCodes()).isEqualTo("PLATINUM");
        assertThat(campaign.getCashbackRate()).isEqualByComparingTo("0.005");
        assertThat(campaign.getMinTransactionAmount()).isEqualByComparingTo("50.00");
        assertThat(campaign.getMaxCashbackPerTransaction()).isEqualByComparingTo("30.00");
        assertThat(campaign.getMaxCashbackPerCustomer()).isEqualByComparingTo("300.00");
        assertThat(campaign.isEnrollmentRequired()).isFalse();
    }

    @Test
    void grantsExtraHalfPercentWithoutEnrollment() {
        EvaluationResult result = evaluatePlatinum("TXN-EXTRA-1", "120.00",
                LocalDateTime.of(2026, 11, 15, 12, 0));

        List<EvaluationResult.Grant> grants = grantsOf(result);
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).cashbackAmount()).isEqualByComparingTo("0.60");
        assertThat(grants.get(0).status()).isEqualTo(RewardGrantStatus.GRANTED.name());
    }

    @Test
    void appliesOnBothWindowBoundaries() {
        assertThat(grantsOf(evaluatePlatinum("TXN-EXTRA-START", "120.00",
                LocalDateTime.of(2026, 11, 1, 0, 0)))).hasSize(1);
        assertThat(grantsOf(evaluatePlatinum("TXN-EXTRA-END", "120.00",
                LocalDateTime.of(2027, 1, 31, 23, 59)))).hasSize(1);
    }

    @Test
    void skipsSpendingOutsideTheWindow() {
        assertThat(skipReasonsOf(evaluatePlatinum("TXN-EXTRA-BEFORE", "120.00",
                LocalDateTime.of(2026, 10, 31, 23, 59)))).containsExactly("OUTSIDE_WINDOW");
        assertThat(skipReasonsOf(evaluatePlatinum("TXN-EXTRA-AFTER", "120.00",
                LocalDateTime.of(2027, 2, 1, 0, 0)))).containsExactly("OUTSIDE_WINDOW");
    }

    @Test
    void skipsOtherCardTiersAndAmountsBelowTheMinimum() {
        assertThat(skipReasonsOf(evaluate("TXN-EXTRA-STANDARD", "120.00",
                LocalDateTime.of(2026, 11, 15, 12, 0), "CLASSIC"))).containsExactly("PRODUCT_NOT_ELIGIBLE");
        assertThat(skipReasonsOf(evaluatePlatinum("TXN-EXTRA-SMALL", "40.00",
                LocalDateTime.of(2026, 11, 15, 12, 0)))).containsExactly("AMOUNT_TOO_LOW");
    }
}
