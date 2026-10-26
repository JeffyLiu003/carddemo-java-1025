package com.carddemo.reconciliation.interfaces.rest.dto;

import com.carddemo.reconciliation.domain.model.ReconciliationItem;

import java.math.BigDecimal;

public record ReconciliationItemResponse(
        String itemId,
        String runId,
        String checkType,
        String referenceId,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        BigDecimal difference,
        String status,
        boolean resolved,
        String resolutionNote,
        String detail) {

    public static ReconciliationItemResponse from(ReconciliationItem item) {
        return new ReconciliationItemResponse(
                item.getItemId(),
                item.getRunId(),
                item.getCheckType().name(),
                item.getReferenceId(),
                item.getExpectedAmount(),
                item.getActualAmount(),
                item.getDifference(),
                item.getStatus().name(),
                item.isResolved(),
                item.getResolutionNote(),
                item.getDetail());
    }
}
