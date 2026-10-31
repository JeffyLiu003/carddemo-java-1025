package com.carddemo.loan.domain.service;

import com.carddemo.loan.domain.service.AmortizationCalculator.ScheduleLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 等额本息计算：月供公式、逐期利息、末期本金调整（设计 §2.2）。
 */
class AmortizationCalculatorTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 3, 15);

    @Test
    void computesMonthlyPaymentWithHalfUpRounding() {
        assertThat(AmortizationCalculator.monthlyPayment(new BigDecimal("1000.00"),
                new BigDecimal("0.1200"), 3)).isEqualByComparingTo("340.02");
        assertThat(AmortizationCalculator.monthlyPayment(new BigDecimal("12000.00"),
                new BigDecimal("0.1800"), 12)).isEqualByComparingTo("1100.16");
        assertThat(AmortizationCalculator.monthlyPayment(new BigDecimal("1200.00"),
                new BigDecimal("0.0000"), 12)).isEqualByComparingTo("100.00");
    }

    @Test
    void buildsThreeInstallmentSchedule() {
        List<ScheduleLine> lines = AmortizationCalculator.buildSchedule(new BigDecimal("1000.00"),
                new BigDecimal("0.1200"), 3, ANCHOR);

        assertThat(lines).hasSize(3);
        assertLine(lines.get(0), 1, "330.02", "10.00", "340.02");
        assertLine(lines.get(1), 2, "333.32", "6.70", "340.02");
        // 末期本金取剩余本金，利息按实际剩余计算，总额略高于标准月供
        assertLine(lines.get(2), 3, "336.66", "3.37", "340.03");
        assertThat(totalPrincipal(lines)).isEqualByComparingTo("1000.00");
    }

    @Test
    void buildsTwelveInstallmentScheduleWithFinalAdjustment() {
        List<ScheduleLine> lines = AmortizationCalculator.buildSchedule(new BigDecimal("12000.00"),
                new BigDecimal("0.1800"), 12, ANCHOR);

        assertThat(lines).hasSize(12);
        assertLine(lines.get(0), 1, "920.16", "180.00", "1100.16");
        assertLine(lines.get(5), 6, "991.27", "108.89", "1100.16");
        assertLine(lines.get(11), 12, "1083.93", "16.26", "1100.19");
        assertThat(totalPrincipal(lines)).isEqualByComparingTo("12000.00");
    }

    @Test
    void zeroRateSplitsPrincipalEvenly() {
        List<ScheduleLine> lines = AmortizationCalculator.buildSchedule(new BigDecimal("1200.00"),
                new BigDecimal("0.0000"), 12, ANCHOR);

        assertThat(lines).allSatisfy(line -> {
            assertThat(line.interestDue()).isEqualByComparingTo("0.00");
            assertThat(line.principalDue()).isEqualByComparingTo("100.00");
            assertThat(line.totalDue()).isEqualByComparingTo("100.00");
        });
        assertThat(totalPrincipal(lines)).isEqualByComparingTo("1200.00");
    }

    @Test
    void dueDatesFallOnTheSameDayEveryMonth() {
        List<ScheduleLine> lines = AmortizationCalculator.buildSchedule(new BigDecimal("1000.00"),
                new BigDecimal("0.1200"), 3, ANCHOR);

        assertThat(lines.get(0).dueDate()).isEqualTo(ANCHOR.plusMonths(1));
        assertThat(lines.get(1).dueDate()).isEqualTo(ANCHOR.plusMonths(2));
        assertThat(lines.get(2).dueDate()).isEqualTo(ANCHOR.plusMonths(3));
    }

    @Test
    void keepsMonthEndAnchorsInsteadOfCompoundingTheShortMonth() {
        // 基准日为月末时，各期按基准日逐期加月：10-31 → 11-30 → 12-31 → 01-31
        List<ScheduleLine> lines = AmortizationCalculator.buildSchedule(new BigDecimal("1000.00"),
                new BigDecimal("0.1200"), 3, LocalDate.of(2026, 10, 31));

        assertThat(lines.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(lines.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(lines.get(2).dueDate()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    private static void assertLine(ScheduleLine line, int installmentNo, String principal, String interest,
                                   String total) {
        assertThat(line.installmentNo()).isEqualTo(installmentNo);
        assertThat(line.principalDue()).isEqualByComparingTo(principal);
        assertThat(line.interestDue()).isEqualByComparingTo(interest);
        assertThat(line.totalDue()).isEqualByComparingTo(total);
    }

    private static BigDecimal totalPrincipal(List<ScheduleLine> lines) {
        return lines.stream().map(ScheduleLine::principalDue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
