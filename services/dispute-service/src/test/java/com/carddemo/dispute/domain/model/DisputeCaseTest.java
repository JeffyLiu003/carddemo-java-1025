package com.carddemo.dispute.domain.model;

import com.carddemo.dispute.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 争议案件状态机（设计 §5.2）：合法链路、非法迁移、临时入账限额。
 */
class DisputeCaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0);

    @Test
    void opensCaseInOpenStatus() {
        DisputeCase disputeCase = openCase();

        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(disputeCase.getProvisionalCreditAmount()).isEqualByComparingTo("0.00");
        assertThat(disputeCase.getOpenedAt()).isEqualTo(NOW);
        assertThat(disputeCase.getClosedAt()).isNull();
    }

    @Test
    void walksLegalChainToWonThenClosed() {
        DisputeCase disputeCase = openCase();

        disputeCase.review(NOW.plusHours(1));
        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);

        disputeCase.grantProvisionalCredit(new BigDecimal("100.00"), NOW.plusHours(2));
        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.PROVISIONAL_CREDIT);
        assertThat(disputeCase.getProvisionalCreditAmount()).isEqualByComparingTo("100.00");

        disputeCase.chargeback(NOW.plusHours(3));
        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.CHARGEBACK);

        disputeCase.resolve(true, NOW.plusHours(4));
        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.WON);
        assertThat(disputeCase.getClosedAt()).isEqualTo(NOW.plusHours(4));

        disputeCase.resolve(true, NOW.plusHours(5));
        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.CLOSED);
    }

    @Test
    void resolvesToLostWhenMerchantWins() {
        DisputeCase disputeCase = openCase();
        disputeCase.review(NOW);
        disputeCase.grantProvisionalCredit(new BigDecimal("250.00"), NOW);
        disputeCase.chargeback(NOW);

        disputeCase.resolve(false, NOW);

        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.LOST);
        assertThat(disputeCase.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsReviewWhenNotOpen() {
        DisputeCase disputeCase = openCase();
        disputeCase.review(NOW);

        assertThatThrownBy(() -> disputeCase.review(NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("DISPUTE_NOT_OPEN"));
    }

    @Test
    void rejectsProvisionalCreditBeforeReview() {
        DisputeCase disputeCase = openCase();

        assertThatThrownBy(() -> disputeCase.grantProvisionalCredit(new BigDecimal("10.00"), NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("DISPUTE_NOT_UNDER_REVIEW"));
    }

    @Test
    void rejectsProvisionalCreditAboveDisputedAmount() {
        DisputeCase disputeCase = openCase();
        disputeCase.review(NOW);

        assertThatThrownBy(() -> disputeCase.grantProvisionalCredit(new BigDecimal("250.01"), NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo("PROVISIONAL_CREDIT_EXCEEDS_DISPUTE_AMOUNT"));
        assertThat(disputeCase.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(disputeCase.getProvisionalCreditAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsNonPositiveProvisionalCredit() {
        DisputeCase disputeCase = openCase();
        disputeCase.review(NOW);

        assertThatThrownBy(() -> disputeCase.grantProvisionalCredit(BigDecimal.ZERO, NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PROVISIONAL_CREDIT_AMOUNT"));
    }

    @Test
    void rejectsChargebackBeforeProvisionalCredit() {
        DisputeCase disputeCase = openCase();
        disputeCase.review(NOW);

        assertThatThrownBy(() -> disputeCase.chargeback(NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("DISPUTE_NOT_PROVISIONAL_CREDIT"));
    }

    @Test
    void rejectsResolveBeforeChargeback() {
        DisputeCase disputeCase = openCase();

        assertThatThrownBy(() -> disputeCase.resolve(true, NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("DISPUTE_NOT_CHARGEBACK"));
    }

    @Test
    void rejectsNonPositiveDisputedAmount() {
        assertThatThrownBy(() -> DisputeCase.open("case-1", "txn-1", "00000000001", "00000000001",
                "FRAUD", new BigDecimal("-1.00"), NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_DISPUTE_AMOUNT"));
    }

    private static DisputeCase openCase() {
        return DisputeCase.open("case-1", "txn-1", "00000000001", "00000000001", "FRAUD",
                new BigDecimal("250.00"), NOW);
    }
}
