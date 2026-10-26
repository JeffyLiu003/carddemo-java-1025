package com.carddemo.fx.domain.model;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxQuoteTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 26, 4, 0);

    private static FxQuote quote(String from, String to, String amount, String rate) {
        return FxQuote.create("quote-1", from, to, new BigDecimal(amount), new BigDecimal(rate), CREATED_AT);
    }

    @Test
    void computesConvertedAmountHalfUpToTwoDecimals() {
        FxQuote usdCny = quote("USD", "CNY", "100.00", "7.1");

        assertThat(usdCny.getSourceAmount()).isEqualByComparingTo("100.00");
        assertThat(usdCny.getRate().toPlainString()).isEqualTo("7.100000");
        assertThat(usdCny.getConvertedAmount()).isEqualByComparingTo("710.00");

        FxQuote usdEur = quote("USD", "EUR", "33.33", "0.92");
        assertThat(usdEur.getConvertedAmount()).isEqualByComparingTo("30.66");
    }

    @Test
    void quoteExpiresFiveMinutesAfterCreation() {
        FxQuote quote = quote("USD", "CNY", "100.00", "7.1");

        assertThat(quote.getExpiresAt()).isEqualTo(CREATED_AT.plusMinutes(5));
        assertThat(quote.isExpired(CREATED_AT.plusMinutes(4).plusSeconds(59))).isFalse();
        assertThat(quote.isExpired(CREATED_AT.plusMinutes(5))).isTrue();
    }

    @Test
    void consumeMarksQuoteAndRejectsSecondConsumption() {
        FxQuote quote = quote("USD", "CNY", "100.00", "7.1");

        quote.consume(CREATED_AT.plusMinutes(1));

        assertThat(quote.isConsumed()).isTrue();
        assertThatThrownBy(() -> quote.consume(CREATED_AT.plusMinutes(2)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("QUOTE_ALREADY_CONSUMED");
    }

    @Test
    void expiredQuoteCannotBeConsumed() {
        FxQuote quote = quote("USD", "CNY", "100.00", "7.1");

        assertThatThrownBy(() -> quote.consume(CREATED_AT.plusMinutes(5)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("QUOTE_EXPIRED");
        assertThat(quote.isConsumed()).isFalse();
    }

    @Test
    void rejectsSameCurrencyAndNonPositiveAmount() {
        assertThatThrownBy(() -> quote("USD", "usd", "100.00", "7.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("SAME_CURRENCY");
        assertThatThrownBy(() -> quote("USD", "CNY", "0.00", "7.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_QUOTE_AMOUNT");
    }
}
