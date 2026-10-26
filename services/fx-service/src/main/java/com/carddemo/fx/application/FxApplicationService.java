package com.carddemo.fx.application;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import com.carddemo.fx.domain.exception.NotFoundException;
import com.carddemo.fx.domain.model.CurrencyCode;
import com.carddemo.fx.domain.model.FxConversion;
import com.carddemo.fx.domain.model.FxQuote;
import com.carddemo.fx.domain.model.FxRate;
import com.carddemo.fx.domain.repository.FxConversionRepository;
import com.carddemo.fx.domain.repository.FxQuoteRepository;
import com.carddemo.fx.domain.repository.FxRateRepository;
import com.carddemo.fx.domain.repository.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 外汇用例：汇率 upsert、报价、执行换汇与查询。
 */
@Service
@Transactional
public class FxApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final FxRateRepository rates;
    private final FxQuoteRepository quotes;
    private final FxConversionRepository conversions;
    private final Clock clock;

    public FxApplicationService(FxRateRepository rates,
                                FxQuoteRepository quotes,
                                FxConversionRepository conversions,
                                Clock clock) {
        this.rates = rates;
        this.quotes = quotes;
        this.conversions = conversions;
        this.clock = clock;
    }

    /**
     * 汇率 upsert：同一 (base, quote) 覆盖汇率与生效时间，否则新建。
     */
    public FxRate upsertRate(UpsertRateCommand command) {
        String base = CurrencyCode.normalize(command.baseCurrency());
        String quote = CurrencyCode.normalize(command.quoteCurrency());
        CurrencyCode.requireDifferent(base, quote);
        LocalDateTime effectiveAt = command.effectiveAt() == null
                ? LocalDateTime.now(clock)
                : command.effectiveAt();

        Optional<FxRate> existing = rates.findByPair(base, quote);
        if (existing.isPresent()) {
            FxRate rate = existing.get();
            rate.update(command.rate(), effectiveAt);
            return rates.save(rate);
        }
        return rates.save(FxRate.create(UUID.randomUUID().toString(), base, quote,
                command.rate(), effectiveAt));
    }

    @Transactional(readOnly = true)
    public PageResult<FxRate> listRates(int page, int size) {
        return rates.search(page, size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    /**
     * 报价：币种不同、金额大于零、直接货币对汇率存在；有效期 5 分钟。
     */
    public FxQuote quote(QuoteCommand command) {
        String from = CurrencyCode.normalize(command.fromCurrency());
        String to = CurrencyCode.normalize(command.toCurrency());
        CurrencyCode.requireDifferent(from, to);

        FxRate rate = rates.findByPair(from, to)
                .orElseThrow(() -> new NotFoundException("RATE_NOT_FOUND",
                        "FX rate " + from + "/" + to + " not found"));
        if (!rate.isActive()) {
            throw new BusinessRuleException("RATE_INACTIVE",
                    "FX rate " + from + "/" + to + " is inactive.");
        }

        FxQuote quote = FxQuote.create(UUID.randomUUID().toString(), from, to,
                command.amount(), rate.getRate(), LocalDateTime.now(clock));
        return quotes.save(quote);
    }

    /**
     * 执行换汇：报价未过期且未消费；成功后标记 consumed 并生成换汇记录。
     */
    public FxConversion execute(String quoteId) {
        FxQuote quote = quotes.lockByQuoteId(quoteId)
                .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND",
                        "Quote " + quoteId + " not found"));
        LocalDateTime now = LocalDateTime.now(clock);
        quote.consume(now);
        quotes.save(quote);
        return conversions.save(FxConversion.fromQuote(UUID.randomUUID().toString(), quote, now));
    }

    @Transactional(readOnly = true)
    public PageResult<FxConversion> listConversions(int page, int size) {
        return conversions.search(page, size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }
}
