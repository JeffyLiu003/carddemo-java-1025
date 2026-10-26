package com.carddemo.billing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 账单计算口径：利息、最低还款、期初余额、账期最后一天与到期日、支付状态流转。
 */
class StatementTest {

    private static final String ACCOUNT_ID = "00000000001";
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 10, 26, 5, 0);

    private static Statement statement(String closing, String cycleCredit, String cycleDebit, String apr) {
        return Statement.generate("ST-1", ACCOUNT_ID, YearMonth.of(2026, 10), new BigDecimal(closing),
                new BigDecimal(cycleCredit), new BigDecimal(cycleDebit), new BigDecimal(apr), GENERATED_AT);
    }

    @Test
    void calculatesInterestMinimumPaymentAndDates() {
        Statement statement = statement("1940.00", "200.00", "-300.00", "0.1899");

        assertThat(statement.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(statement.getPeriod()).isEqualTo("2026-10");
        assertThat(statement.getClosingBalance()).isEqualByComparingTo("1940.00");
        assertThat(statement.getPurchases()).isEqualByComparingTo("200.00");
        assertThat(statement.getPayments()).isEqualByComparingTo("300.00");
        assertThat(statement.getOpeningBalance()).isEqualByComparingTo("2040.00");
        assertThat(statement.getFees()).isEqualByComparingTo("0.00");
        assertThat(statement.getInterest()).isEqualByComparingTo("30.70");
        assertThat(statement.getMinimumPayment()).isEqualByComparingTo("58.20");
        assertThat(statement.getCycleDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(statement.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(statement.getStatus()).isEqualTo(StatementStatus.GENERATED);
        assertThat(statement.getGeneratedAt()).isEqualTo(GENERATED_AT);
        assertThat(statement.getPaidAt()).isNull();
        assertThat(statement.amountDue()).isEqualByComparingTo("1940.00");
    }

    @Test
    void roundsInterestHalfUp() {
        assertThat(statement("1000.05", "0.00", "0.00", "0.1899").getInterest())
                .isEqualByComparingTo("15.83");
    }

    @Test
    void onlyNegativeCycleDebitCountsAsPayments() {
        Statement statement = statement("500.00", "120.00", "40.00", "0.1899");

        assertThat(statement.getPayments()).isEqualByComparingTo("0.00");
        assertThat(statement.getOpeningBalance()).isEqualByComparingTo("380.00");
    }

    @Test
    void appliesMinimumPaymentFloorAndCap() {
        assertThat(statement("300.00", "0.00", "0.00", "0.1899").getMinimumPayment())
                .isEqualByComparingTo("25.00");
        assertThat(statement("10.00", "0.00", "0.00", "0.1899").getMinimumPayment())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void zeroBalanceStatementHasNoInterestAndNothingDue() {
        Statement statement = statement("0.00", "0.00", "0.00", "0.1899");

        assertThat(statement.getInterest()).isEqualByComparingTo("0.00");
        assertThat(statement.getMinimumPayment()).isEqualByComparingTo("0.00");
        assertThat(statement.amountDue()).isEqualByComparingTo("0.00");
        assertThat(statement.requiresPayment()).isFalse();
    }

    @Test
    void markPaidIsIdempotentAndKeepsFirstPaymentTime() {
        Statement statement = statement("1940.00", "0.00", "0.00", "0.1899");
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 26, 6, 0);

        statement.markPaid(paidAt);
        statement.markPaid(paidAt.plusDays(1));

        assertThat(statement.getStatus()).isEqualTo(StatementStatus.PAID);
        assertThat(statement.getPaidAt()).isEqualTo(paidAt);
        assertThat(statement.requiresPayment()).isFalse();
    }

    @Test
    void reportsOverdueOnlyAfterDueDate() {
        Statement statement = statement("1940.00", "0.00", "0.00", "0.1899");
        LocalDate dueDate = statement.getDueDate();

        assertThat(statement.statusOn(dueDate)).isEqualTo(StatementStatus.GENERATED);
        assertThat(statement.statusOn(dueDate.plusDays(1))).isEqualTo(StatementStatus.OVERDUE);

        statement.markPaid(LocalDateTime.of(2026, 10, 26, 6, 0));

        assertThat(statement.statusOn(dueDate.plusDays(1))).isEqualTo(StatementStatus.PAID);
        assertThat(statement.getStatus()).isEqualTo(StatementStatus.PAID);
    }
}
