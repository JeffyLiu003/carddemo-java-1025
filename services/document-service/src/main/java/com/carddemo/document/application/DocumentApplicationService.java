package com.carddemo.document.application;

import com.carddemo.document.domain.exception.NotFoundException;
import com.carddemo.document.domain.model.DocumentMetadata;
import com.carddemo.document.domain.model.DocumentUpload;
import com.carddemo.document.domain.model.OwnerType;
import com.carddemo.document.domain.repository.DocumentPage;
import com.carddemo.document.domain.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档用例服务：上传、元数据读取、文本读取、分页、归档、删除（设计 15 §4.2）。
 */
@Service
@Transactional
public class DocumentApplicationService {

    private final DocumentRepository documents;

    public DocumentApplicationService(DocumentRepository documents) {
        this.documents = documents;
    }

    /**
     * 上传元数据 + 文本：生成 document_id，状态 ACTIVE，sizeBytes 由文本 UTF-8 字节数决定。
     */
    public DocumentMetadata upload(DocumentUpload upload) {
        return documents.saveDocument(
                DocumentMetadata.upload(UUID.randomUUID().toString(), upload, LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public DocumentMetadata get(String documentId) {
        return documents.findByDocumentId(documentId)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND",
                        "Document " + documentId + " not found"));
    }

    @Transactional(readOnly = true)
    public String content(String documentId) {
        return get(documentId).getContentText();
    }

    /**
     * 归档幂等：ACTIVE → ARCHIVED，已归档再次归档仍返回 ARCHIVED。
     */
    public DocumentMetadata archive(String documentId) {
        DocumentMetadata document = get(documentId);
        document.archive();
        return documents.saveDocument(document);
    }

    public void delete(String documentId) {
        DocumentMetadata document = get(documentId);
        documents.deleteDocument(document.getDocumentId());
    }

    /**
     * 按 ownerType / ownerId 过滤（AND），按创建时间倒序。
     */
    @Transactional(readOnly = true)
    public DocumentPage list(OwnerType ownerType, String ownerId, int page, int size) {
        return documents.findPage(ownerType, blankToNull(ownerId), page, size);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
