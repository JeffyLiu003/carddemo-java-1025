package com.carddemo.fx.domain.model;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import com.carddemo.fx.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxRateTest {

    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 10, 26, 0, 0);

    private static FxRate rate(String base, String quote, String value) {
        return FxRate.create("pair-1", base, quote, new BigDecimal(value), EFFECTIVE_AT);
    }

    @Test
    void createsNormalizedPairWithSixDecimalRate() {
        FxRate fxRate = rate("usd", "cny", "7.1");

        assertThat(fxRate.getBaseCurrency()).isEqualTo("USD");
        assertThat(fxRate.getQuoteCurrency()).isEqualTo("CNY");
        assertThat(fxRate.getRate().toPlainString()).isEqualTo("7.100000");
        assertThat(fxRate.getEffectiveAt()).isEqualTo(EFFECTIVE_AT);
        assertThat(fxRate.isActive()).isTrue();
    }

    @Test
    void rejectsSameCurrency() {
        assertThatThrownBy(() -> rate("USD", "USD", "1.0"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("SAME_CURRENCY");
    }

    @Test
    void rejectsMalformedCurrency() {
        assertThatThrownBy(() -> rate("US", "CNY", "7.1"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_CURRENCY");
        assertThatThrownBy(() -> rate("USD", "  ", "7.1"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_CURRENCY");
    }

    @Test
    void rejectsNonPositiveRate() {
        assertThatThrownBy(() -> rate("USD", "CNY", "0"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_RATE");
        assertThatThrownBy(() -> rate("USD", "CNY", "-7.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_RATE");
    }

    @Test
    void updateReplacesRateAndEffectiveAt() {
        FxRate fxRate = rate("USD", "CNY", "7.1");
        LocalDateTime next = EFFECTIVE_AT.plusDays(1);

        fxRate.update(new BigDecimal("7.25"), next);

        assertThat(fxRate.getRate().toPlainString()).isEqualTo("7.250000");
        assertThat(fxRate.getEffectiveAt()).isEqualTo(next);
    }
}
