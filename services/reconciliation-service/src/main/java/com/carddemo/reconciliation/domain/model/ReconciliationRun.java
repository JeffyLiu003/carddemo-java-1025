package com.carddemo.reconciliation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 对账运行聚合根。先以 RUNNING 落库再调用下游，成功置 COMPLETED 并写入汇总口径，
 * 失败置 FAILED 并保留 error_message（设计 §5：运行记录不随下游失败回滚）。
 */
@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "period", length = 7, nullable = false, updatable = false)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RunStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "batch_count", nullable = false)
    private int batchCount;

    @Column(name = "batch_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal batchAmount = ZERO;

    @Column(name = "journal_count", nullable = false)
    private int journalCount;

    @Column(name = "journal_debit_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal journalDebitTotal = ZERO;

    @Column(name = "journal_credit_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal journalCreditTotal = ZERO;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    @Column(name = "mismatch_count", nullable = false)
    private int mismatchCount;

    @Column(name = "missing_journal_count", nullable = false)
    private int missingJournalCount;

    @Column(name = "missing_batch_count", nullable = false)
    private int missingBatchCount;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    @Column(name = "error_message", length = MESSAGE_MAX_LENGTH)
    private String errorMessage;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReconciliationRun() {
        // for JPA
    }

    private ReconciliationRun(String runId, LocalDate businessDate, YearMonth period, LocalDateTime startedAt) {
        this.runId = runId;
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate must not be null");
        this.period = Objects.requireNonNull(period, "period must not be null").toString();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.status = RunStatus.RUNNING;
    }

    public static ReconciliationRun start(String runId, LocalDate businessDate, YearMonth period,
                                          LocalDateTime startedAt) {
        return new ReconciliationRun(runId, businessDate, period, startedAt);
    }

    /**
     * 下游读取与计算全部成功后调用：写入汇总口径，matched 由差异数是否为 0 决定。
     */
    public ReconciliationRun complete(ReconciliationTotals totals, LocalDateTime completedAt) {
        this.status = RunStatus.COMPLETED;
        this.completedAt = completedAt;
        this.batchCount = totals.batchCount();
        this.batchAmount = totals.batchAmount();
        this.journalCount = totals.journalCount();
        this.journalDebitTotal = totals.journalDebitTotal();
        this.journalCreditTotal = totals.journalCreditTotal();
        this.matchCount = totals.matchCount();
        this.mismatchCount = totals.mismatchCount();
        this.missingJournalCount = totals.missingJournalCount();
        this.missingBatchCount = totals.missingBatchCount();
        this.matched = totals.matched();
        return this;
    }

    /**
     * 下游不可用时调用：保留失败原因，供历史查询与排查（设计 §5）。
     */
    public ReconciliationRun fail(String errorMessage, LocalDateTime completedAt) {
        this.status = RunStatus.FAILED;
        this.completedAt = completedAt;
        this.matched = false;
        this.errorMessage = truncate(errorMessage);
        return this;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MESSAGE_MAX_LENGTH ? message.substring(0, MESSAGE_MAX_LENGTH) : message;
    }

    public String getRunId() {
        return runId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getPeriod() {
        return period;
    }

    public RunStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public int getBatchCount() {
        return batchCount;
    }

    public BigDecimal getBatchAmount() {
        return batchAmount;
    }

    public int getJournalCount() {
        return journalCount;
    }

    public BigDecimal getJournalDebitTotal() {
        return journalDebitTotal;
    }

    public BigDecimal getJournalCreditTotal() {
        return journalCreditTotal;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public int getMismatchCount() {
        return mismatchCount;
    }

    public int getMissingJournalCount() {
        return missingJournalCount;
    }

    public int getMissingBatchCount() {
        return missingBatchCount;
    }

    public boolean isMatched() {
        return matched;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getVersion() {
        return version;
    }
}
