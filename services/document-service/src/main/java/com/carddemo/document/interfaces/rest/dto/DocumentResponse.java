package com.carddemo.document.interfaces.rest.dto;

import com.carddemo.document.domain.model.DocumentMetadata;

import java.time.LocalDateTime;

/**
 * 文档元数据响应；文本内容通过 GET /api/v1/documents/{id}/content 获取，不在此返回。
 */
public record DocumentResponse(
        String documentId,
        String documentType,
        String ownerType,
        String ownerId,
        String fileName,
        String contentType,
        long sizeBytes,
        String status,
        LocalDateTime createdAt,
        long version) {

    public static DocumentResponse from(DocumentMetadata document) {
        return new DocumentResponse(
                document.getDocumentId(),
                document.getDocumentType().name(),
                document.getOwnerType().name(),
                document.getOwnerId(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getCreatedAt(),
                document.getVersion());
    }
}
