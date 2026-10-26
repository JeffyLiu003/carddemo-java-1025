package com.carddemo.fx.domain.model;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 货币对汇率聚合。不变量：买卖币种不同、汇率大于零、汇率保留 6 位小数；
 * 同一 (base_currency, quote_currency) 只有一条记录（upsert）。
 */
@Entity
@Table(name = "fx_rate")
public class FxRate {

    private static final int RATE_SCALE = 6;

    @Id
    @Column(name = "pair_id", length = 36, nullable = false, updatable = false)
    private String pairId;

    @Column(name = "base_currency", length = 3, nullable = false, updatable = false)
    private String baseCurrency;

    @Column(name = "quote_currency", length = 3, nullable = false, updatable = false)
    private String quoteCurrency;

    @Column(name = "rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal rate;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FxRate() {
        // for JPA
    }

    private FxRate(String pairId, String baseCurrency, String quoteCurrency,
                   BigDecimal rate, LocalDateTime effectiveAt) {
        this.pairId = pairId;
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
        this.effectiveAt = effectiveAt;
        this.active = true;
    }

    public static FxRate create(String pairId, String baseCurrency, String quoteCurrency,
                                BigDecimal rate, LocalDateTime effectiveAt) {
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        String base = CurrencyCode.normalize(baseCurrency);
        String quote = CurrencyCode.normalize(quoteCurrency);
        CurrencyCode.requireDifferent(base, quote);
        return new FxRate(pairId, base, quote, normalizeRate(rate), effectiveAt);
    }

    /**
     * upsert 覆盖已有货币对的汇率与生效时间。
     */
    public void update(BigDecimal newRate, LocalDateTime newEffectiveAt) {
        Objects.requireNonNull(newEffectiveAt, "effectiveAt must not be null");
        this.rate = normalizeRate(newRate);
        this.effectiveAt = newEffectiveAt;
        this.active = true;
    }

    private static BigDecimal normalizeRate(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessRuleException("INVALID_RATE", "FX rate must be greater than zero.");
        }
        return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    public String getPairId() {
        return pairId;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
