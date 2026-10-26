package com.carddemo.transaction.interfaces.rest.dto;

import com.carddemo.transaction.application.PostingTypeView;
import com.carddemo.transaction.domain.model.PostingType;

import java.util.List;

/**
 * 过账类型详情响应：类型 + 规则（按 legNo 升序）。
 */
public record PostingTypeDetailResponse(
        String typeCode,
        String name,
        String description,
        String accountEffect,
        boolean active,
        List<PostingRuleResponse> rules) {

    public static PostingTypeDetailResponse from(PostingTypeView view) {
        PostingType type = view.type();
        return new PostingTypeDetailResponse(type.getTypeCode(), type.getName(), type.getDescription(),
                type.getAccountEffect().name(), type.isActive(),
                view.rules().stream().map(PostingRuleResponse::from).toList());
    }
}
