package com.carddemo.document.interfaces.rest.dto;

import com.carddemo.document.domain.model.DocumentType;
import com.carddemo.document.domain.model.OwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 文档上传请求。请求体中的大小（若有）一律忽略：sizeBytes 由 contentText 的 UTF-8 字节数计算。
 */
public record UploadDocumentRequest(
        @NotNull(message = "Document Type must be supplied.")
        DocumentType documentType,

        @NotNull(message = "Owner Type must be supplied.")
        OwnerType ownerType,

        @NotBlank(message = "Owner ID must be supplied.")
        @Size(max = 36, message = "Owner ID must be 36 characters or less.")
        @Pattern(regexp = "[A-Za-z0-9-]+",
                message = "Owner ID must contain letters, digits and hyphens only.")
        String ownerId,

        @NotBlank(message = "File Name must be supplied.")
        @Size(max = 200, message = "File Name must be 200 characters or less.")
        @Pattern(regexp = "[^/\\\\]+", message = "File Name must not contain a path.")
        String fileName,

        @NotBlank(message = "Content Type must be supplied.")
        @Size(max = 100, message = "Content Type must be 100 characters or less.")
        @Pattern(regexp = "text/[A-Za-z0-9.+-]+", message = "Content Type must be a text media type.")
        String contentType,

        @NotNull(message = "Content Text must be supplied.")
        String contentText) {
}
