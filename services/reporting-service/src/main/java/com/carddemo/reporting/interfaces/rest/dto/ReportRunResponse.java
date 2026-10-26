package com.carddemo.reporting.interfaces.rest.dto;

import com.carddemo.reporting.application.ReportRunView;
import com.carddemo.reporting.domain.model.ReportRun;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表运行详情：运行汇总 + 结果行（POST /api/v1/report-runs 的 201、GET /{runId}）。
 */
public record ReportRunResponse(String runId,
                                String reportCode,
                                LocalDate businessDate,
                                String status,
                                LocalDateTime generatedAt,
                                LocalDateTime completedAt,
                                int rowCount,
                                String message,
                                List<ReportRowResponse> rows) {

    public static ReportRunResponse from(ReportRunView view) {
        ReportRun run = view.run();
        return new ReportRunResponse(
                run.getRunId(),
                run.getReportCode(),
                run.getBusinessDate(),
                run.getStatus().name(),
                run.getGeneratedAt(),
                run.getCompletedAt(),
                run.getRowCount(),
                run.getMessage(),
                view.rows().stream().map(ReportRowResponse::from).toList());
    }
}
