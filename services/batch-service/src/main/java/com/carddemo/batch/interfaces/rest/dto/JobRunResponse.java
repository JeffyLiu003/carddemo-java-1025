package com.carddemo.batch.interfaces.rest.dto;

import com.carddemo.batch.domain.model.JobRun;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record JobRunResponse(
        String runId,
        String jobCode,
        LocalDate businessDate,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String message) {

    public static JobRunResponse from(JobRun run) {
        return new JobRunResponse(
                run.getRunId(),
                run.getJobCode(),
                run.getBusinessDate(),
                run.getStatus().name(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getMessage());
    }
}
