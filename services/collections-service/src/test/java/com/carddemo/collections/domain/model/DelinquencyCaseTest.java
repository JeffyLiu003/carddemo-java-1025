package com.carddemo.collections.domain.model;

import com.carddemo.collections.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 逾期案件聚合：分桶边界、承诺日期校验、状态迁移与结案后不可变。
 */
class DelinquencyCaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 9, 30);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private static DelinquencyCase openCase(int daysPastDue) {
        return DelinquencyCase.open("case-1", "00000000006", daysPastDue,
                new BigDecimal("1234.56"), NOW);
    }

    @ParameterizedTest(name = "daysPastDue={0} -> {1}")
    @CsvSource({
            "1, B1",
            "30, B1",
            "31, B2",
            "60, B2",
            "61, B3",
            "90, B3",
            "91, B4"})
    void assignsBucketAtBoundaries(int daysPastDue, String bucket) {
        assertThat(DelinquencyBucket.of(daysPastDue).name()).isEqualTo(bucket);
        assertThat(openCase(daysPastDue).getBucket().name()).isEqualTo(bucket);
    }

    @Test
    void opensAsOpenWithNormalisedAmount() {
        DelinquencyCase delinquencyCase = DelinquencyCase.open("case-1", "00000000006", 45,
                new BigDecimal("100.005"), NOW);

        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(delinquencyCase.getBucket()).isEqualTo(DelinquencyBucket.B2);
        assertThat(delinquencyCase.getOutstandingAmount()).isEqualByComparingTo("100.01");
        assertThat(delinquencyCase.getCreatedAt()).isEqualTo(NOW);
        assertThat(delinquencyCase.getUpdatedAt()).isEqualTo(NOW);
        assertThat(delinquencyCase.getPromiseDate()).isNull();
        assertThat(delinquencyCase.getResolvedAt()).isNull();
    }

    @Test
    void rejectsNonPositiveDaysPastDueAndAmount() {
        assertThatThrownBy(() -> openCase(0))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_DAYS_PAST_DUE"));
        assertThatThrownBy(() -> DelinquencyCase.open("case-1", "00000000006", 10,
                BigDecimal.ZERO, NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_OUTSTANDING_AMOUNT"));
    }

    @Test
    void promiseDateMustBeStrictlyInTheFuture() {
        DelinquencyCase delinquencyCase = openCase(45);

        assertThatThrownBy(() -> delinquencyCase.promiseToPay(TODAY, NOW, TODAY))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PROMISE_DATE"));
        assertThatThrownBy(() -> delinquencyCase.promiseToPay(TODAY.minusDays(1), NOW, TODAY))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PROMISE_DATE"));
        assertThatThrownBy(() -> delinquencyCase.promiseToPay(null, NOW, TODAY))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PROMISE_DATE"));
        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.OPEN);
    }

    @Test
    void promiseToPayMovesOpenToPtpAndCanBeRescheduled() {
        DelinquencyCase delinquencyCase = openCase(45);

        LocalDate first = TODAY.plusDays(7);
        delinquencyCase.promiseToPay(first, NOW, TODAY);

        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.PTP);
        assertThat(delinquencyCase.getPromiseDate()).isEqualTo(first);
        assertThat(delinquencyCase.getUpdatedAt()).isEqualTo(NOW);

        LocalDate second = TODAY.plusDays(14);
        delinquencyCase.promiseToPay(second, NOW.plusDays(1), TODAY.plusDays(1));

        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.PTP);
        assertThat(delinquencyCase.getPromiseDate()).isEqualTo(second);
    }

    @Test
    void recalculateUpdatesDaysAndBucket() {
        DelinquencyCase delinquencyCase = openCase(30);

        delinquencyCase.recalculate(91, NOW.plusDays(61), TODAY.plusDays(61));

        assertThat(delinquencyCase.getDaysPastDue()).isEqualTo(91);
        assertThat(delinquencyCase.getBucket()).isEqualTo(DelinquencyBucket.B4);
        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.OPEN);
    }

    @Test
    void brokenPromiseFallsBackToOpenOnRecalculate() {
        DelinquencyCase delinquencyCase = openCase(45);
        LocalDate promiseDate = TODAY.plusDays(3);
        delinquencyCase.promiseToPay(promiseDate, NOW, TODAY);

        delinquencyCase.recalculate(50, NOW.plusDays(10), promiseDate);

        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(delinquencyCase.getPromiseDate()).isNull();
        assertThat(delinquencyCase.getBucket()).isEqualTo(DelinquencyBucket.B2);
    }

    @Test
    void livePromiseSurvivesRecalculate() {
        DelinquencyCase delinquencyCase = openCase(45);
        LocalDate promiseDate = TODAY.plusDays(3);
        delinquencyCase.promiseToPay(promiseDate, NOW, TODAY);

        delinquencyCase.recalculate(61, NOW.plusDays(1), TODAY.plusDays(1));

        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.PTP);
        assertThat(delinquencyCase.getPromiseDate()).isEqualTo(promiseDate);
        assertThat(delinquencyCase.getBucket()).isEqualTo(DelinquencyBucket.B3);
    }

    @Test
    void resolveFromOpenOrPtpIsTerminal() {
        DelinquencyCase fromOpen = openCase(45).resolve(NOW);
        assertThat(fromOpen.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(fromOpen.getResolvedAt()).isEqualTo(NOW);

        DelinquencyCase fromPtp = openCase(45);
        fromPtp.promiseToPay(TODAY.plusDays(7), NOW, TODAY);
        fromPtp.resolve(NOW.plusDays(1));
        assertThat(fromPtp.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(fromPtp.getResolvedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void resolvedCaseRejectsEveryFurtherChange() {
        DelinquencyCase delinquencyCase = openCase(45).resolve(NOW);

        assertThatThrownBy(() -> delinquencyCase.recalculate(60, NOW, TODAY))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CASE_RESOLVED"));
        assertThatThrownBy(() -> delinquencyCase.promiseToPay(TODAY.plusDays(7), NOW, TODAY))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CASE_RESOLVED"));
        assertThatThrownBy(() -> delinquencyCase.resolve(NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CASE_RESOLVED"));

        assertThat(delinquencyCase.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(delinquencyCase.getDaysPastDue()).isEqualTo(45);
    }
}
