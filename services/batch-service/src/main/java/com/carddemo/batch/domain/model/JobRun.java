package com.carddemo.batch.domain.model;

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
 * 作业运行记录：先以 RUNNING 落库，执行后转 COMPLETED/FAILED，同一 jobCode+businessDate 每次执行都留一条。
 */
@Entity
@Table(name = "job_run")
public class JobRun {

    private static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;

    @Column(name = "job_code", length = 32, nullable = false, updatable = false)
    private String jobCode;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RunStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "message", length = MESSAGE_MAX_LENGTH)
    private String message;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected JobRun() {
        // for JPA
    }

    private JobRun(String runId, String jobCode, LocalDate businessDate, LocalDateTime startedAt) {
        this.runId = Objects.requireNonNull(runId, "runId must not be null");
        this.jobCode = Objects.requireNonNull(jobCode, "jobCode must not be null");
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.status = RunStatus.RUNNING;
    }

    public static JobRun start(String runId, String jobCode, LocalDate businessDate, LocalDateTime startedAt) {
        return new JobRun(runId, jobCode, businessDate, startedAt);
    }

    public JobRun complete(String message, LocalDateTime completedAt) {
        this.status = RunStatus.COMPLETED;
        this.message = truncate(message);
        this.completedAt = completedAt;
        return this;
    }

    public JobRun fail(String message, LocalDateTime completedAt) {
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

    public String getJobCode() {
        return jobCode;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
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

    public String getMessage() {
        return message;
    }

    public long getVersion() {
        return version;
    }
}
