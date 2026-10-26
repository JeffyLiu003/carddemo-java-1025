package com.carddemo.document.domain.model;

/**
 * 文档上传草稿：元数据 + 文本内容。sizeBytes 不在此列，由 {@link DocumentMetadata} 按 UTF-8 字节数计算。
 */
public record DocumentUpload(
        DocumentType documentType,
        OwnerType ownerType,
        String ownerId,
        String fileName,
        String contentType,
        String contentText) {
}
