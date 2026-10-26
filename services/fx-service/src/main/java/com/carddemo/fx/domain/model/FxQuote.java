package com.carddemo.fx.domain.model;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 换汇报价。报价 5 分钟内有效，执行一次后标记 consumed，不可重复消费。
 */
@Entity
@Table(name = "fx_quote")
public class FxQuote {

    public static final Duration QUOTE_TTL = Duration.ofMinutes(5);

    private static final int AMOUNT_SCALE = 2;

    @Id
    @Column(name = "quote_id", length = 36, nullable = false, updatable = false)
    private String quoteId;

    @Column(name = "from_currency", length = 3, nullable = false, updatable = false)
    private String fromCurrency;

    @Column(name = "to_currency", length = 3, nullable = false, updatable = false)
    private String toCurrency;

    @Column(name = "source_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal sourceAmount;

    @Column(name = "rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal rate;

    @Column(name = "converted_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal convertedAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    protected FxQuote() {
        // for JPA
    }

    private FxQuote(String quoteId, String fromCurrency, String toCurrency, BigDecimal sourceAmount,
                    BigDecimal rate, BigDecimal convertedAmount, LocalDateTime createdAt) {
        this.quoteId = quoteId;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.sourceAmount = sourceAmount;
        this.rate = rate;
        this.convertedAmount = convertedAmount;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(QUOTE_TTL);
        this.consumed = false;
    }

    /**
     * 报价：convertedAmount = sourceAmount × rate，HALF_UP 保留 2 位；有效期 now + 5 分钟。
     */
    public static FxQuote create(String quoteId, String fromCurrency, String toCurrency,
                                 BigDecimal sourceAmount, BigDecimal rate, LocalDateTime createdAt) {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        String from = CurrencyCode.normalize(fromCurrency);
        String to = CurrencyCode.normalize(toCurrency);
        CurrencyCode.requireDifferent(from, to);
        if (sourceAmount == null || sourceAmount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUOTE_AMOUNT",
                    "Quote source amount must be greater than zero.");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new BusinessRuleException("INVALID_RATE", "FX rate must be greater than zero.");
        }
        BigDecimal amount = sourceAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedRate = rate.setScale(6, RoundingMode.HALF_UP);
        BigDecimal converted = amount.multiply(normalizedRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        return new FxQuote(quoteId, from, to, amount, normalizedRate, converted, createdAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 消费报价：已消费 409 QUOTE_ALREADY_CONSUMED，已过期 409 QUOTE_EXPIRED。
     */
    public void consume(LocalDateTime now) {
        if (consumed) {
            throw new BusinessRuleException("QUOTE_ALREADY_CONSUMED",
                    "Quote " + quoteId + " has already been consumed.");
        }
        if (isExpired(now)) {
            throw new BusinessRuleException("QUOTE_EXPIRED",
                    "Quote " + quoteId + " expired at " + expiresAt + ".");
        }
        this.consumed = true;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public String getFromCurrency() {
        return fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public BigDecimal getSourceAmount() {
        return sourceAmount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isConsumed() {
        return consumed;
    }
}
