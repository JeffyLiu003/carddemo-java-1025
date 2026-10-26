package com.carddemo.document.domain.repository;

import com.carddemo.document.domain.model.DocumentMetadata;
import com.carddemo.document.domain.model.OwnerType;

import java.util.Optional;

/**
 * 文档仓储端口。实现见 infrastructure.persistence.DocumentJpaRepository。
 */
public interface DocumentRepository {

    Optional<DocumentMetadata> findByDocumentId(String documentId);

    DocumentPage findPage(OwnerType ownerType, String ownerId, int page, int size);

    DocumentMetadata saveDocument(DocumentMetadata document);

    void deleteDocument(String documentId);
}
