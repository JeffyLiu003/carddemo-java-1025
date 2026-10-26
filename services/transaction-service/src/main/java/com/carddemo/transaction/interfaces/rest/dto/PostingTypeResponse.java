package com.carddemo.transaction.interfaces.rest.dto;

import com.carddemo.transaction.application.PostingTypeView;
import com.carddemo.transaction.domain.model.PostingType;

/**
 * 过账类型响应（不含规则）。
 */
public record PostingTypeResponse(
        String typeCode,
        String name,
        String description,
        String accountEffect,
        boolean active) {

    public static PostingTypeResponse from(PostingTypeView view) {
        PostingType type = view.type();
        return new PostingTypeResponse(type.getTypeCode(), type.getName(), type.getDescription(),
                type.getAccountEffect().name(), type.isActive());
    }
}
