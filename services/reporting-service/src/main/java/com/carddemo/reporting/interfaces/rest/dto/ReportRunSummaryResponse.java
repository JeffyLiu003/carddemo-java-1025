package com.carddemo.reporting.interfaces.rest.dto;

import com.carddemo.reporting.domain.model.ReportRun;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运行历史条目：不含结果行（明细走 GET /api/v1/report-runs/{runId}）。
 */
public record ReportRunSummaryResponse(String runId,
                                       String reportCode,
                                       LocalDate businessDate,
                                       String status,
                                       LocalDateTime generatedAt,
                                       LocalDateTime completedAt,
                                       int rowCount,
                                       String message) {

    public static ReportRunSummaryResponse from(ReportRun run) {
        return new ReportRunSummaryResponse(
                run.getRunId(),
                run.getReportCode(),
                run.getBusinessDate(),
                run.getStatus().name(),
                run.getGeneratedAt(),
                run.getCompletedAt(),
                run.getRowCount(),
                run.getMessage());
    }
}
