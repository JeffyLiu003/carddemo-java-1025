package com.carddemo.reference.interfaces.rest.dto;

import com.carddemo.reference.domain.model.ReferenceItem;

public record ReferenceItemResponse(
        String itemId,
        String domain,
        String code,
        String label,
        int sortOrder,
        boolean active,
        long version) {

    public static ReferenceItemResponse from(ReferenceItem item) {
        return new ReferenceItemResponse(item.getItemId(), item.getDomain(), item.getCode(),
                item.getLabel(), item.getSortOrder(), item.isActive(), item.getVersion());
    }
}
