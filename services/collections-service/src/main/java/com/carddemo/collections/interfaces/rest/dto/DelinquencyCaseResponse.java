package com.carddemo.collections.interfaces.rest.dto;

import com.carddemo.collections.domain.model.DelinquencyCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DelinquencyCaseResponse(
        String caseId,
        String accountId,
        int daysPastDue,
        String bucket,
        BigDecimal outstandingAmount,
        String status,
        LocalDate promiseDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt) {

    public static DelinquencyCaseResponse from(DelinquencyCase delinquencyCase) {
        return new DelinquencyCaseResponse(
                delinquencyCase.getCaseId(),
                delinquencyCase.getAccountId(),
                delinquencyCase.getDaysPastDue(),
                delinquencyCase.getBucket().name(),
                delinquencyCase.getOutstandingAmount(),
                delinquencyCase.getStatus().name(),
                delinquencyCase.getPromiseDate(),
                delinquencyCase.getCreatedAt(),
                delinquencyCase.getUpdatedAt(),
                delinquencyCase.getResolvedAt());
    }
}
