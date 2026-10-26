package com.carddemo.ledger.domain.model;

import com.carddemo.ledger.domain.exception.BusinessRuleException;
import com.carddemo.ledger.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountingPeriodTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 4, 0);

    @Test
    void opensPeriodInOpenStatus() {
        AccountingPeriod period = AccountingPeriod.open("2026-11");

        assertThat(period.getStatus()).isEqualTo(PeriodStatus.OPEN);
        assertThat(period.isOpen()).isTrue();
        assertThat(period.getClosedAt()).isNull();
        assertThat(period.asYearMonth()).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    void closeMarksPeriodClosedWithTimestamp() {
        AccountingPeriod period = AccountingPeriod.open("2026-11");

        period.close(NOW);

        assertThat(period.getStatus()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(period.isOpen()).isFalse();
        assertThat(period.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void closedPeriodCannotBeClosedAgain() {
        AccountingPeriod period = AccountingPeriod.open("2026-11");
        period.close(NOW);

        assertThatThrownBy(() -> period.close(NOW.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("PERIOD_ALREADY_CLOSED");
    }

    @Test
    void rejectsMalformedPeriod() {
        assertThatThrownBy(() -> AccountingPeriod.parsePeriod("2026-13"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_PERIOD");
        assertThatThrownBy(() -> AccountingPeriod.parsePeriod("October"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_PERIOD");
    }
}
