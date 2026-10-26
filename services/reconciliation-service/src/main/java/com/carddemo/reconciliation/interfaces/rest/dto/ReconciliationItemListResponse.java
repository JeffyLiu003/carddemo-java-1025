package com.carddemo.reconciliation.interfaces.rest.dto;

import com.carddemo.reconciliation.domain.model.ReconciliationItem;

import java.util.List;

public record ReconciliationItemListResponse(List<ReconciliationItemResponse> items) {

    public static ReconciliationItemListResponse from(List<ReconciliationItem> items) {
        return new ReconciliationItemListResponse(items.stream().map(ReconciliationItemResponse::from).toList());
    }
}
