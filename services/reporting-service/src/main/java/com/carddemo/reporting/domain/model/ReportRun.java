package com.carddemo.reporting.domain.model;

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
 * 报表运行聚合根。先以 RUNNING 落库再调用下游，成功置 COMPLETED 并写入 rowCount，
 * 失败置 FAILED 并保留 message（设计 §2.2：运行记录不随下游失败回滚）。
 */
@Entity
@Table(name = "report_run")
public class ReportRun {

    private static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;

    @Column(name = "report_code", length = 40, nullable = false, updatable = false)
    private String reportCode;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RunStatus status;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "message", length = MESSAGE_MAX_LENGTH)
    private String message;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReportRun() {
        // for JPA
    }

    private ReportRun(String runId, String reportCode, LocalDate businessDate, LocalDateTime generatedAt) {
        this.runId = Objects.requireNonNull(runId, "runId must not be null");
        this.reportCode = Objects.requireNonNull(reportCode, "reportCode must not be null");
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate must not be null");
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        this.status = RunStatus.RUNNING;
    }

    public static ReportRun start(String runId, String reportCode, LocalDate businessDate,
                                  LocalDateTime generatedAt) {
        return new ReportRun(runId, reportCode, businessDate, generatedAt);
    }

    /**
     * 结果行全部落库后调用：写入行数与摘要。
     */
    public ReportRun complete(int rowCount, String message, LocalDateTime completedAt) {
        this.status = RunStatus.COMPLETED;
        this.rowCount = rowCount;
        this.message = truncate(message);
        this.completedAt = completedAt;
        return this;
    }

    /**
     * 下游不可用时调用：保留失败原因，供历史查询与排查。
     */
    public ReportRun fail(String message, LocalDateTime completedAt) {
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

    public String getReportCode() {
        return reportCode;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public RunStatus getStatus() {
        return status;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public int getRowCount() {
        return rowCount;
    }

    public String getMessage() {
        return message;
    }

    public long getVersion() {
        return version;
    }
}
