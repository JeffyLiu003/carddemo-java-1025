package com.carddemo.reconciliation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * EOD 运行聚合根。当前步骤只有对账，成功后回写 reconciliation_run_id，
 * 失败时保留 message 并把异常继续抛给调用方（接口 503）。
 */
@Entity
@Table(name = "eod_run")
public class EodRun {

    private static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RunStatus status;

    @Column(name = "reconciliation_run_id", length = 36)
    private String reconciliationRunId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "message", length = MESSAGE_MAX_LENGTH)
    private String message;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected EodRun() {
        // for JPA
    }

    private EodRun(String runId, LocalDate businessDate, LocalDateTime startedAt) {
        this.runId = runId;
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.status = RunStatus.RUNNING;
    }

    public static EodRun start(String runId, LocalDate businessDate, LocalDateTime startedAt) {
        return new EodRun(runId, businessDate, startedAt);
    }

    public EodRun complete(String reconciliationRunId, String message, LocalDateTime completedAt) {
        this.status = RunStatus.COMPLETED;
        this.reconciliationRunId = reconciliationRunId;
        this.message = truncate(message);
        this.completedAt = completedAt;
        return this;
    }

    public EodRun fail(String message, LocalDateTime completedAt) {
        this.status = RunStatus.FAILED;
        this.message = truncate(message);
        this.completedAt = completedAt;
        return this;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MESSAGE_MAX_LENGTH ? text.substring(0, MESSAGE_MAX_LENGTH) : text;
    }

    public String getRunId() {
        return runId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public RunStatus getStatus() {
        return status;
    }

    public String getReconciliationRunId() {
        return reconciliationRunId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getMessage() {
        return message;
    }

    public long getVersion() {
        return version;
    }
}
