package com.carddemo.document.domain.model;

import com.carddemo.document.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 文档元数据聚合根（DOCUMENT_METADATA）。状态机：ACTIVE → ARCHIVED（重复归档幂等）。
 * 契约见 docs/design/15-operations-support.md §4。
 */
@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final Pattern CONTENT_TYPE = Pattern.compile("text/[A-Za-z0-9.+-]+");
    private static final Pattern OWNER_ID = Pattern.compile("[A-Za-z0-9-]+");

    @Id
    @Column(name = "document_id", length = 36, nullable = false, updatable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 16, nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", length = 16, nullable = false)
    private OwnerType ownerType;

    @Column(name = "owner_id", length = 36, nullable = false)
    private String ownerId;

    @Column(name = "file_name", length = 200, nullable = false)
    private String fileName;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    /**
     * 文本内容。列类型由迁移脚本声明为 TEXT（PostgreSQL 原生长文本；H2 在 PostgreSQL 兼容模式下
     * 解析为不限长 CHARACTER VARYING）。此处不指定 length，保持与两端列宽的校验兼容。
     */
    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private DocumentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DocumentMetadata() {
        // for JPA
    }

    /**
     * 上传：状态一律 ACTIVE，sizeBytes 取自 contentText 的 UTF-8 字节数（忽略调用方传入的大小）。
     */
    public static DocumentMetadata upload(String documentId, DocumentUpload upload, LocalDateTime now) {
        Objects.requireNonNull(upload, "upload must not be null");
        DocumentMetadata document = new DocumentMetadata();
        document.documentId = requireText(documentId, 36, "Document ID");
        document.documentType = requireDocumentType(upload.documentType());
        document.ownerType = requireOwnerType(upload.ownerType());
        document.ownerId = requireOwnerId(upload.ownerId());
        document.fileName = requireFileName(upload.fileName());
        document.contentType = requireContentType(upload.contentType());
        document.contentText = requireContentText(upload.contentText());
        document.sizeBytes = document.contentText.getBytes(StandardCharsets.UTF_8).length;
        document.status = DocumentStatus.ACTIVE;
        document.createdAt = now == null ? LocalDateTime.now() : now;
        return document;
    }

    /**
     * 归档：ACTIVE → ARCHIVED；已归档时幂等返回，不改变状态。
     */
    public void archive() {
        if (status == DocumentStatus.ARCHIVED) {
            return;
        }
        this.status = DocumentStatus.ARCHIVED;
    }

    public boolean isArchived() {
        return status == DocumentStatus.ARCHIVED;
    }

    private static DocumentType requireDocumentType(DocumentType value) {
        if (value == null) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Document Type must be supplied.");
        }
        return value;
    }

    private static OwnerType requireOwnerType(OwnerType value) {
        if (value == null) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Owner Type must be supplied.");
        }
        return value;
    }

    private static String requireOwnerId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Owner ID must be supplied.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 36) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Owner ID must be 36 characters or less.");
        }
        if (!OWNER_ID.matcher(trimmed).matches()) {
            throw new BusinessRuleException(VALIDATION_FAILED,
                    "Owner ID must contain letters, digits and hyphens only.");
        }
        return trimmed;
    }

    private static String requireFileName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(VALIDATION_FAILED, "File Name must be supplied.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 200) {
            throw new BusinessRuleException(VALIDATION_FAILED, "File Name must be 200 characters or less.");
        }
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new BusinessRuleException(VALIDATION_FAILED, "File Name must not contain a path.");
        }
        return trimmed;
    }

    private static String requireContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Content Type must be supplied.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 100) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Content Type must be 100 characters or less.");
        }
        if (!CONTENT_TYPE.matcher(trimmed).matches()) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Content Type must be a text media type.");
        }
        return trimmed;
    }

    private static String requireContentText(String value) {
        if (value == null) {
            throw new BusinessRuleException(VALIDATION_FAILED, "Content Text must be supplied.");
        }
        return value;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(VALIDATION_FAILED, field + " must be supplied.");
        }
        if (value.length() > maxLength) {
            throw new BusinessRuleException(VALIDATION_FAILED,
                    field + " must be " + maxLength + " characters or less.");
        }
        return value;
    }

    public String getDocumentId() {
        return documentId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentText() {
        return contentText;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
