package com.carddemo.account.domain.model;

import com.carddemo.account.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 账户贷记流水。通用能力：营销返现、调账等场景按 {@code creditId} 幂等冲减账户欠款
 * （docs/design/17-premium-cashback-campaign.md §3.2）。
 */
@Entity
@Table(name = "account_credit")
public class AccountCredit {

    @Id
    @Column(name = "credit_id", length = 36, nullable = false, updatable = false)
    private String creditId;

    @Column(name = "account_id", length = 11, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "source_type", length = 30, nullable = false)
    private String sourceType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AccountCredit() {
        // for JPA
    }

    private AccountCredit(String creditId, String accountId, String sourceType, BigDecimal amount,
                          String description, LocalDateTime createdAt) {
        this.creditId = creditId;
        this.accountId = accountId;
        this.sourceType = sourceType;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static AccountCredit create(String creditId, String accountId, String sourceType,
                                       BigDecimal amount, String description, LocalDateTime createdAt) {
        if (creditId == null || creditId.isBlank() || creditId.length() > 36) {
            throw new BusinessRuleException("INVALID_CREDIT_ID", "Credit id must be 1 to 36 characters.");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessRuleException("INVALID_CREDIT_ACCOUNT", "Account id must be supplied.");
        }
        if (sourceType == null || sourceType.isBlank() || sourceType.length() > 30) {
            throw new BusinessRuleException("INVALID_CREDIT_SOURCE", "Source type must be 1 to 30 characters.");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new BusinessRuleException("INVALID_CREDIT_AMOUNT",
                    "Credit amount must be greater than zero with at most 2 decimal places.");
        }
        if (description != null && description.length() > 200) {
            throw new BusinessRuleException("INVALID_CREDIT_DESCRIPTION",
                    "Credit description must be at most 200 characters.");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        return new AccountCredit(creditId.trim(), accountId, sourceType, amount, description, createdAt);
    }

    public String getCreditId() {
        return creditId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
