package com.carddemo.reference.interfaces.rest.dto;

import com.carddemo.reference.domain.model.ReferenceItem;

import java.util.List;

public record ReferenceListResponse(List<ReferenceItemResponse> items, long total) {

    public static ReferenceListResponse from(List<ReferenceItem> items) {
        return new ReferenceListResponse(items.stream().map(ReferenceItemResponse::from).toList(),
                items.size());
    }
}
