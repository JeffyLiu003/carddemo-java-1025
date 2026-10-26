package com.carddemo.fx.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 换汇记录。每条报价最多生成一条换汇记录（quote_id 唯一）。
 */
@Entity
@Table(name = "fx_conversion")
public class FxConversion {

    @Id
    @Column(name = "conversion_id", length = 36, nullable = false, updatable = false)
    private String conversionId;

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

    protected FxConversion() {
        // for JPA
    }

    private FxConversion(String conversionId, String quoteId, String fromCurrency, String toCurrency,
                         BigDecimal sourceAmount, BigDecimal rate, BigDecimal convertedAmount,
                         LocalDateTime createdAt) {
        this.conversionId = conversionId;
        this.quoteId = quoteId;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.sourceAmount = sourceAmount;
        this.rate = rate;
        this.convertedAmount = convertedAmount;
        this.createdAt = createdAt;
    }

    public static FxConversion fromQuote(String conversionId, FxQuote quote, LocalDateTime createdAt) {
        Objects.requireNonNull(quote, "quote must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        return new FxConversion(conversionId, quote.getQuoteId(), quote.getFromCurrency(),
                quote.getToCurrency(), quote.getSourceAmount(), quote.getRate(),
                quote.getConvertedAmount(), createdAt);
    }

    public String getConversionId() {
        return conversionId;
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
}
