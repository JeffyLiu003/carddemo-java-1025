package com.carddemo.fraud.domain.service;

import com.carddemo.fraud.domain.model.FraudDecision;
import com.carddemo.fraud.domain.model.FraudEvaluationInput;
import com.carddemo.fraud.domain.model.FraudRule;
import com.carddemo.fraud.domain.model.FraudRuleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 种子规则（金额>5000、商户 BLOCKED MERCHANT、国家 KP/IR）的命中与分值汇总。
 */
class FraudScoringServiceTest {

    private final FraudScoringService scoring = new FraudScoringService();

    private static final FraudRule AMOUNT_RULE = new FraudRule("AMOUNT_GT", "Large amount",
            FraudRuleType.AMOUNT_GT, new BigDecimal("5000.00"), null, 60, true);

    private static final FraudRule MERCHANT_RULE = new FraudRule("MERCHANT_BLOCKED", "Blocked merchant",
            FraudRuleType.MERCHANT_BLOCKED, null, "BLOCKED MERCHANT", 100, true);

    private static final FraudRule COUNTRY_RULE = new FraudRule("COUNTRY_BLOCKED", "Blocked country",
            FraudRuleType.COUNTRY_BLOCKED, null, "KP,IR", 80, true);

    private static final List<FraudRule> RULES = List.of(AMOUNT_RULE, MERCHANT_RULE, COUNTRY_RULE);

    private static FraudEvaluationInput input(String amount, String merchantName, String country) {
        return new FraudEvaluationInput(amount == null ? null : new BigDecimal(amount), merchantName, country);
    }

    @Test
    void approvesTransactionBelowAllThresholds() {
        FraudAssessment assessment = scoring.assess(input("100.00", "Corner Grocery", "US"), RULES);

        assertThat(assessment.score()).isZero();
        assertThat(assessment.matchedRules()).isEmpty();
        assertThat(assessment.decision()).isEqualTo(FraudDecision.APPROVE);
    }

    @Test
    void amountMustBeStrictlyGreaterThanThreshold() {
        assertThat(scoring.assess(input("5000.00", "Corner Grocery", "US"), RULES).matchedRules()).isEmpty();

        FraudAssessment above = scoring.assess(input("5000.01", "Corner Grocery", "US"), RULES);

        assertThat(above.score()).isEqualTo(60);
        assertThat(above.matchedRules()).containsExactly("AMOUNT_GT");
        assertThat(above.decision()).isEqualTo(FraudDecision.REVIEW);
    }

    @Test
    void matchesBlockedMerchantIgnoringCase() {
        FraudAssessment assessment = scoring.assess(input("20.00", "blocked merchant store", "US"), RULES);

        assertThat(assessment.score()).isEqualTo(100);
        assertThat(assessment.matchedRules()).containsExactly("MERCHANT_BLOCKED");
        assertThat(assessment.decision()).isEqualTo(FraudDecision.REJECT);
    }

    @Test
    void matchesBlockedCountryIgnoringCaseAndWhitespace() {
        FraudAssessment assessment = scoring.assess(input("20.00", "Corner Grocery", " ir "), RULES);

        assertThat(assessment.score()).isEqualTo(80);
        assertThat(assessment.matchedRules()).containsExactly("COUNTRY_BLOCKED");
        assertThat(assessment.decision()).isEqualTo(FraudDecision.REJECT);
    }

    @Test
    void sumsAllMatchedRulesInRuleOrder() {
        FraudAssessment assessment = scoring.assess(input("6000.00", "Blocked Merchant", "KP"), RULES);

        assertThat(assessment.score()).isEqualTo(240);
        assertThat(assessment.matchedRules())
                .containsExactly("AMOUNT_GT", "MERCHANT_BLOCKED", "COUNTRY_BLOCKED");
        assertThat(assessment.decision()).isEqualTo(FraudDecision.REJECT);
    }

    @Test
    void ignoresInactiveRules() {
        FraudRule inactive = new FraudRule("AMOUNT_GT_OFF", "Disabled amount rule",
                FraudRuleType.AMOUNT_GT, new BigDecimal("1.00"), null, 90, false);

        FraudAssessment assessment = scoring.assess(input("6000.00", null, null), List.of(inactive));

        assertThat(assessment.score()).isZero();
        assertThat(assessment.matchedRules()).isEmpty();
        assertThat(assessment.decision()).isEqualTo(FraudDecision.APPROVE);
    }

    @Test
    void mapsScoreBoundariesToDecision() {
        assertThat(FraudDecision.fromScore(49)).isEqualTo(FraudDecision.APPROVE);
        assertThat(FraudDecision.fromScore(50)).isEqualTo(FraudDecision.REVIEW);
        assertThat(FraudDecision.fromScore(79)).isEqualTo(FraudDecision.REVIEW);
        assertThat(FraudDecision.fromScore(80)).isEqualTo(FraudDecision.REJECT);
    }

    @Test
    void treatsMissingMerchantAndCountryAsNoMatch() {
        FraudAssessment assessment = scoring.assess(input("6000.00", null, null), RULES);

        assertThat(assessment.score()).isEqualTo(60);
        assertThat(assessment.matchedRules()).containsExactly("AMOUNT_GT");
    }
}
