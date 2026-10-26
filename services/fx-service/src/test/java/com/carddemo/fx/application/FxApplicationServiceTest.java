package com.carddemo.fx.application;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import com.carddemo.fx.domain.exception.NotFoundException;
import com.carddemo.fx.domain.model.FxConversion;
import com.carddemo.fx.domain.model.FxQuote;
import com.carddemo.fx.domain.model.FxRate;
import com.carddemo.fx.domain.repository.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外汇用例：真实 H2 + Flyway 种子（USD/CNY 7.10、USD/EUR 0.92、EUR/CNY 7.72），
 * 事务回滚保证用例间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FxApplicationServiceTest {

    @Autowired
    private FxApplicationService fx;

    private static FxRate findRate(PageResult<FxRate> page, String base, String quote) {
        return page.items().stream()
                .filter(rate -> rate.getBaseCurrency().equals(base) && rate.getQuoteCurrency().equals(quote))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FX rate " + base + "/" + quote + " not found"));
    }

    @Test
    void listsSeededRates() {
        PageResult<FxRate> rates = fx.listRates(0, 20);

        assertThat(rates.total()).isEqualTo(3);
        assertThat(findRate(rates, "USD", "CNY").getRate()).isEqualByComparingTo("7.10");
        assertThat(findRate(rates, "USD", "EUR").getRate()).isEqualByComparingTo("0.92");
        assertThat(findRate(rates, "EUR", "CNY").getRate()).isEqualByComparingTo("7.72");
    }

    @Test
    void upsertCreatesNewPairAndUpdatesExistingOne() {
        FxRate created = fx.upsertRate(new UpsertRateCommand("gbp", "usd", new BigDecimal("1.27"), null));

        assertThat(created.getBaseCurrency()).isEqualTo("GBP");
        assertThat(created.getQuoteCurrency()).isEqualTo("USD");
        assertThat(created.getRate().toPlainString()).isEqualTo("1.270000");
        assertThat(fx.listRates(0, 20).total()).isEqualTo(4);

        FxRate updated = fx.upsertRate(new UpsertRateCommand("GBP", "USD", new BigDecimal("1.28"),
                LocalDateTime.of(2026, 10, 27, 0, 0)));

        assertThat(updated.getPairId()).isEqualTo(created.getPairId());
        assertThat(updated.getRate().toPlainString()).isEqualTo("1.280000");
        assertThat(updated.getEffectiveAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 0, 0));
        assertThat(fx.listRates(0, 20).total()).isEqualTo(4);
    }

    @Test
    void upsertRejectsSameCurrencyAndNonPositiveRate() {
        assertThatThrownBy(() -> fx.upsertRate(new UpsertRateCommand("USD", "USD",
                new BigDecimal("1.00"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("SAME_CURRENCY");
        assertThatThrownBy(() -> fx.upsertRate(new UpsertRateCommand("USD", "JPY",
                new BigDecimal("0"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_RATE");
    }

    @Test
    void quoteComputesConvertedAmountAndExpiry() {
        FxQuote quote = fx.quote(new QuoteCommand("usd", "cny", new BigDecimal("100.00")));

        assertThat(quote.getQuoteId()).isNotBlank();
        assertThat(quote.getFromCurrency()).isEqualTo("USD");
        assertThat(quote.getToCurrency()).isEqualTo("CNY");
        assertThat(quote.getSourceAmount()).isEqualByComparingTo("100.00");
        assertThat(quote.getRate()).isEqualByComparingTo("7.10");
        assertThat(quote.getConvertedAmount()).isEqualByComparingTo("710.00");
        assertThat(quote.getExpiresAt()).isEqualTo(quote.getCreatedAt().plusMinutes(5));
        assertThat(quote.isConsumed()).isFalse();
    }

    @Test
    void quoteRequiresExistingDirectRate() {
        assertThatThrownBy(() -> fx.quote(new QuoteCommand("GBP", "JPY", new BigDecimal("10.00"))))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("RATE_NOT_FOUND");
    }

    @Test
    void quoteRejectsSameCurrencyAndNonPositiveAmount() {
        assertThatThrownBy(() -> fx.quote(new QuoteCommand("USD", "usd", new BigDecimal("10.00"))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("SAME_CURRENCY");
        assertThatThrownBy(() -> fx.quote(new QuoteCommand("USD", "CNY", new BigDecimal("0.00"))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_QUOTE_AMOUNT");
    }

    @Test
    void executeConsumesQuoteAndRecordsConversionOnce() {
        FxQuote quote = fx.quote(new QuoteCommand("USD", "CNY", new BigDecimal("100.00")));

        FxConversion conversion = fx.execute(quote.getQuoteId());

        assertThat(conversion.getConversionId()).isNotBlank();
        assertThat(conversion.getQuoteId()).isEqualTo(quote.getQuoteId());
        assertThat(conversion.getFromCurrency()).isEqualTo("USD");
        assertThat(conversion.getToCurrency()).isEqualTo("CNY");
        assertThat(conversion.getSourceAmount()).isEqualByComparingTo("100.00");
        assertThat(conversion.getConvertedAmount()).isEqualByComparingTo("710.00");
        assertThat(conversion.getRate()).isEqualByComparingTo("7.10");

        assertThatThrownBy(() -> fx.execute(quote.getQuoteId()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("QUOTE_ALREADY_CONSUMED");

        PageResult<FxConversion> conversions = fx.listConversions(0, 20);
        assertThat(conversions.total()).isEqualTo(1);
        assertThat(conversions.items().get(0).getQuoteId()).isEqualTo(quote.getQuoteId());
    }

    @Test
    void executeUnknownQuoteIsNotFound() {
        assertThatThrownBy(() -> fx.execute("missing-quote"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("QUOTE_NOT_FOUND");
    }
}
