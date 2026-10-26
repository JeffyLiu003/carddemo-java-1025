package com.carddemo.document.interfaces.rest.dto;

import com.carddemo.document.domain.repository.DocumentPage;

import java.util.List;

/**
 * 分页文档列表：{"items": [...], "page": 0, "size": 10, "total": 2}。
 */
public record DocumentPageResponse(
        List<DocumentResponse> items,
        int page,
        int size,
        long total) {

    public static DocumentPageResponse from(DocumentPage documentPage) {
        return new DocumentPageResponse(
                documentPage.items().stream().map(DocumentResponse::from).toList(),
                documentPage.page(),
                documentPage.size(),
                documentPage.total());
    }
}
