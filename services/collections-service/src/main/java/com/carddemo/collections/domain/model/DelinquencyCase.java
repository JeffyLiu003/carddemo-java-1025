package com.carddemo.collections.domain.model;

import com.carddemo.collections.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 逾期案件聚合根：按逾期天数维护分桶，登记承诺还款（PTP），结案后成为终态。
 */
@Entity
@Table(name = "delinquency_case")
public class DelinquencyCase {

    private static final String CODE_CASE_RESOLVED = "CASE_RESOLVED";
    private static final String CODE_INVALID_DAYS_PAST_DUE = "INVALID_DAYS_PAST_DUE";
    private static final String CODE_INVALID_PROMISE_DATE = "INVALID_PROMISE_DATE";

    @Id
    @Column(name = "case_id", length = 36, nullable = false, updatable = false)
    private String caseId;

    @Column(name = "account_id", length = 11, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "days_past_due", nullable = false)
    private int daysPastDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "bucket", length = 2, nullable = false)
    private DelinquencyBucket bucket;

    @Column(name = "outstanding_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal outstandingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private CaseStatus status;

    @Column(name = "promise_date")
    private LocalDate promiseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DelinquencyCase() {
        // for JPA
    }

    private DelinquencyCase(String caseId, String accountId, int daysPastDue,
                            BigDecimal outstandingAmount, LocalDateTime createdAt) {
        this.caseId = Objects.requireNonNull(caseId, "caseId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.daysPastDue = requireDaysPastDue(daysPastDue);
        this.bucket = DelinquencyBucket.of(daysPastDue);
        this.outstandingAmount = requireAmount(outstandingAmount);
        this.status = CaseStatus.OPEN;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static DelinquencyCase open(String caseId, String accountId, int daysPastDue,
                                       BigDecimal outstandingAmount, LocalDateTime createdAt) {
        return new DelinquencyCase(caseId, accountId, daysPastDue, outstandingAmount, createdAt);
    }

    /**
     * 重算逾期天数与分桶。PTP 案件在承诺日到期（含当天）仍未还款时视为承诺失效，退回 OPEN。
     */
    public DelinquencyCase recalculate(int daysPastDue, LocalDateTime now, LocalDate today) {
        ensureNotResolved();
        this.daysPastDue = requireDaysPastDue(daysPastDue);
        this.bucket = DelinquencyBucket.of(this.daysPastDue);
        if (status == CaseStatus.PTP && promiseDate != null && !promiseDate.isAfter(today)) {
            status = CaseStatus.OPEN;
            promiseDate = null;
        }
        this.updatedAt = now;
        return this;
    }

    public DelinquencyCase promiseToPay(LocalDate promiseDate, LocalDateTime now, LocalDate today) {
        ensureNotResolved();
        if (promiseDate == null || !promiseDate.isAfter(today)) {
            throw new BusinessRuleException(CODE_INVALID_PROMISE_DATE,
                    "Promise date must be after " + today + ".");
        }
        this.promiseDate = promiseDate;
        this.status = CaseStatus.PTP;
        this.updatedAt = now;
        return this;
    }

    public DelinquencyCase resolve(LocalDateTime now) {
        ensureNotResolved();
        this.status = CaseStatus.RESOLVED;
        this.resolvedAt = now;
        this.updatedAt = now;
        return this;
    }

    private void ensureNotResolved() {
        if (status == CaseStatus.RESOLVED) {
            throw new BusinessRuleException(CODE_CASE_RESOLVED,
                    "Delinquency case " + caseId + " is resolved and can no longer be changed.");
        }
    }

    private static int requireDaysPastDue(int daysPastDue) {
        if (daysPastDue < 1) {
            throw new BusinessRuleException(CODE_INVALID_DAYS_PAST_DUE,
                    "Days past due must be at least 1, but was " + daysPastDue + ".");
        }
        return daysPastDue;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_OUTSTANDING_AMOUNT",
                    "Outstanding amount must be greater than zero.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public String getCaseId() {
        return caseId;
    }

    public String getAccountId() {
        return accountId;
    }

    public int getDaysPastDue() {
        return daysPastDue;
    }

    public DelinquencyBucket getBucket() {
        return bucket;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public LocalDate getPromiseDate() {
        return promiseDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }
}
