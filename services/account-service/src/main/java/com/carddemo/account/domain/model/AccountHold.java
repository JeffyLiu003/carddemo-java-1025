package com.carddemo.account.domain.model;

import com.carddemo.account.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 资金冻结聚合。授权、支付受理、争议等场景通过 hold 占用可用额度，
 * 释放或过期后额度自动归还。
 */
@Entity
@Table(name = "account_hold")
public class AccountHold {

    @Id
    @Column(name = "hold_id", length = 36, nullable = false, updatable = false)
    private String holdId;

    @Column(name = "account_id", length = 11, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private HoldStatus status;

    @Column(name = "reference_type", length = 20, nullable = false)
    private String referenceType;

    @Column(name = "reference_id", length = 64, nullable = false)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AccountHold() {
        // for JPA
    }

    private AccountHold(String holdId, String accountId, BigDecimal amount, String referenceType,
                        String referenceId, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.holdId = holdId;
        this.accountId = accountId;
        this.amount = amount;
        this.status = HoldStatus.ACTIVE;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static AccountHold create(String holdId, String accountId, BigDecimal amount,
                                     String referenceType, String referenceId,
                                     LocalDateTime createdAt, LocalDateTime expiresAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_HOLD_AMOUNT",
                    "Hold amount must be greater than zero.");
        }
        if (referenceType == null || referenceType.isBlank()) {
            throw new BusinessRuleException("INVALID_HOLD_REFERENCE", "Reference type must be supplied.");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new BusinessRuleException("INVALID_HOLD_REFERENCE", "Reference id must be supplied.");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new BusinessRuleException("INVALID_HOLD_EXPIRY", "Hold expiry must be in the future.");
        }
        return new AccountHold(holdId, accountId, amount, referenceType, referenceId, createdAt, expiresAt);
    }

    /**
     * AH-06：只有 ACTIVE 的冻结可以释放；重复释放由应用层幂等处理。
     */
    public void release(LocalDateTime now) {
        if (status != HoldStatus.ACTIVE) {
            throw new BusinessRuleException("HOLD_NOT_RELEASABLE",
                    "Hold " + holdId + " is " + status + " and cannot be released.");
        }
        this.status = HoldStatus.RELEASED;
        this.releasedAt = now;
    }

    /**
     * AH-07：过期冻结不再占用额度。
     */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public String getHoldId() {
        return holdId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public HoldStatus getStatus() {
        return status;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public long getVersion() {
        return version;
    }
}
