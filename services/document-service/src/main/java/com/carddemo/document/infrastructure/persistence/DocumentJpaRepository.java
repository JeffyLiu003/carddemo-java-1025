package com.carddemo.document.infrastructure.persistence;

import com.carddemo.document.domain.model.DocumentMetadata;
import com.carddemo.document.domain.model.OwnerType;
import com.carddemo.document.domain.repository.DocumentPage;
import com.carddemo.document.domain.repository.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentJpaRepository extends JpaRepository<DocumentMetadata, String>, DocumentRepository {

    Page<DocumentMetadata> findByOwnerType(OwnerType ownerType, Pageable pageable);

    Page<DocumentMetadata> findByOwnerId(String ownerId, Pageable pageable);

    Page<DocumentMetadata> findByOwnerTypeAndOwnerId(OwnerType ownerType, String ownerId, Pageable pageable);

    @Override
    default Optional<DocumentMetadata> findByDocumentId(String documentId) {
        return findById(documentId);
    }

    @Override
    default DocumentPage findPage(OwnerType ownerType, String ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("documentId")));
        Page<DocumentMetadata> result;
        if (ownerType != null && ownerId != null) {
            result = findByOwnerTypeAndOwnerId(ownerType, ownerId, pageable);
        } else if (ownerType != null) {
            result = findByOwnerType(ownerType, pageable);
        } else if (ownerId != null) {
            result = findByOwnerId(ownerId, pageable);
        } else {
            result = findAll(pageable);
        }
        return new DocumentPage(result.getContent(), page, size, result.getTotalElements());
    }

    @Override
    default DocumentMetadata saveDocument(DocumentMetadata document) {
        return saveAndFlush(document);
    }

    @Override
    default void deleteDocument(String documentId) {
        deleteById(documentId);
        flush();
    }
}
