package com.carddemo.limit.domain.model;

import com.carddemo.limit.domain.exception.BusinessRuleException;
import com.carddemo.limit.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 限额定义聚合根。不变量：额度大于零、已用不超过额度、释放后已用不为负、
 * 调整后的额度不低于已用。
 */
@Entity
@Table(name = "limit_definition")
public class LimitDefinition {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int MONEY_SCALE = 2;
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    @Id
    @Column(name = "limit_id", length = 36, nullable = false, updatable = false)
    private String limitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 16, nullable = false, updatable = false)
    private ScopeType scopeType;

    @Column(name = "scope_id", length = 64, nullable = false, updatable = false)
    private String scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", length = 16, nullable = false, updatable = false)
    private LimitType limitType;

    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(name = "used_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal usedAmount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LimitDefinition() {
        // for JPA
    }

    private LimitDefinition(String limitId, ScopeType scopeType, String scopeId, LimitType limitType,
                            String currency, BigDecimal amount) {
        this.limitId = limitId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.limitType = limitType;
        this.currency = currency;
        this.amount = amount;
        this.usedAmount = ZERO;
        this.active = true;
    }

    public static LimitDefinition create(String limitId, ScopeType scopeType, String scopeId,
                                         LimitType limitType, String currency, BigDecimal amount,
                                         boolean active) {
        Objects.requireNonNull(scopeType, "scopeType must not be null");
        Objects.requireNonNull(limitType, "limitType must not be null");
        LimitDefinition definition = new LimitDefinition(limitId, scopeType, requireScopeId(scopeId),
                limitType, normalizeCurrency(currency), requirePositiveAmount(amount, "Limit amount"));
        definition.active = active;
        return definition;
    }

    /**
     * 调整额度：新额度必须大于零且不得低于已用金额。
     */
    public void adjust(BigDecimal newAmount) {
        BigDecimal normalized = requirePositiveAmount(newAmount, "Limit amount");
        if (normalized.compareTo(usedAmount) < 0) {
            throw new BusinessRuleException("LIMIT_BELOW_USED",
                    "Limit " + limitId + " cannot be lowered to " + normalized.toPlainString()
                            + " which is below the used amount " + usedAmount.toPlainString() + ".");
        }
        this.amount = normalized;
    }

    /**
     * 占用额度：累计已用不得超过额度，否则 409 LIMIT_EXCEEDED。
     */
    public void reserve(BigDecimal requested) {
        if (!active) {
            throw new BusinessRuleException("LIMIT_INACTIVE",
                    "Limit " + limitId + " is inactive and cannot be used.");
        }
        BigDecimal normalized = requirePositiveAmount(requested, "Usage amount");
        BigDecimal afterReserve = usedAmount.add(normalized);
        if (afterReserve.compareTo(amount) > 0) {
            throw new BusinessRuleException("LIMIT_EXCEEDED",
                    "Limit " + limitId + " exceeded: amount " + amount.toPlainString()
                            + ", used " + usedAmount.toPlainString()
                            + ", requested " + normalized.toPlainString() + ".");
        }
        this.usedAmount = afterReserve;
    }

    /**
     * 释放额度：释放金额不得超过已用金额，保证已用不为负。
     */
    public void release(BigDecimal released) {
        BigDecimal normalized = requirePositiveAmount(released, "Release amount");
        BigDecimal afterRelease = usedAmount.subtract(normalized);
        if (afterRelease.signum() < 0) {
            throw new BusinessRuleException("INVALID_LIMIT_RELEASE",
                    "Release amount " + normalized.toPlainString() + " exceeds used amount "
                            + usedAmount.toPlainString() + " of limit " + limitId + ".");
        }
        this.usedAmount = afterRelease;
    }

    public BigDecimal remainingAmount() {
        return amount.subtract(usedAmount);
    }

    /**
     * 可用性判定：仅启用的限额可放行请求。
     */
    public boolean allows(BigDecimal requested) {
        if (requested == null || requested.signum() <= 0) {
            throw new BusinessRuleException("INVALID_LIMIT_AMOUNT",
                    "Checked amount must be greater than zero.");
        }
        return active && requested.setScale(MONEY_SCALE, RoundingMode.HALF_UP).compareTo(remainingAmount()) <= 0;
    }

    private static BigDecimal requirePositiveAmount(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessRuleException("INVALID_LIMIT_AMOUNT", label + " must be greater than zero.");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String requireScopeId(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("INVALID_LIMIT_SCOPE", "Limit scope id must be supplied.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 64) {
            throw new InvalidRequestException("INVALID_LIMIT_SCOPE",
                    "Limit scope id must be 64 characters or less.");
        }
        return trimmed;
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("INVALID_CURRENCY", "Limit currency must be supplied.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_PATTERN.matcher(normalized).matches()) {
            throw new InvalidRequestException("INVALID_CURRENCY",
                    "Limit currency must be a 3-letter ISO code, got " + value + ".");
        }
        return normalized;
    }

    public String getLimitId() {
        return limitId;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public LimitType getLimitType() {
        return limitType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
