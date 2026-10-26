package com.carddemo.reconciliation.interfaces.rest.dto;

import com.carddemo.reconciliation.domain.model.ReconciliationRun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账运行汇总（不含 items），用于历史分页。
 */
public record ReconciliationRunSummaryResponse(
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
        String errorMessage) {

    public static ReconciliationRunSummaryResponse from(ReconciliationRun run) {
        return new ReconciliationRunSummaryResponse(
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
                run.getErrorMessage());
    }
}
