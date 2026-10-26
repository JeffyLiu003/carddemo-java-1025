package com.carddemo.reconciliation.interfaces.rest.dto;

import com.carddemo.reconciliation.domain.model.EodRun;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EodRunResponse(
        String runId,
        LocalDate businessDate,
        String status,
        String reconciliationRunId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String message) {

    public static EodRunResponse from(EodRun run) {
        return new EodRunResponse(
                run.getRunId(),
                run.getBusinessDate(),
                run.getStatus().name(),
                run.getReconciliationRunId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getMessage());
    }
}
