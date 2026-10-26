package com.carddemo.reconciliation.interfaces.rest.dto;

import com.carddemo.reconciliation.application.ReconciliationRunView;
import com.carddemo.reconciliation.domain.model.ReconciliationRun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账运行详情：汇总 + 差异明细（POST /api/v1/reconciliation-runs 的 201、GET /{runId}）。
 */
public record ReconciliationRunResponse(
        String runId,
        LocalDate businessDate,
        String period,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int batchCount,
        BigDecimal batchAmount,
        int journalCount,
        BigDecimal journalDebitTotal,
        BigDecimal journalCreditTotal,
        int matchCount,
        int mismatchCount,
        int missingJournalCount,
        int missingBatchCount,
        boolean matched,
        String errorMessage,
        List<ReconciliationItemResponse> items) {

    public static ReconciliationRunResponse from(ReconciliationRunView view) {
        ReconciliationRun run = view.run();
        return new ReconciliationRunResponse(
                run.getRunId(),
                run.getBusinessDate(),
                run.getPeriod(),
                run.getStatus().name(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getBatchCount(),
                run.getBatchAmount(),
                run.getJournalCount(),
                run.getJournalDebitTotal(),
                run.getJournalCreditTotal(),
                run.getMatchCount(),
                run.getMismatchCount(),
                run.getMissingJournalCount(),
                run.getMissingBatchCount(),
                run.isMatched(),
                run.getErrorMessage(),
                view.items().stream().map(ReconciliationItemResponse::from).toList());
    }
}
