package com.carddemo.document.domain.model;

import com.carddemo.document.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentMetadataTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0);

    @Test
    void uploadStartsActiveAndDerivesSizeFromUtf8Bytes() {
        DocumentMetadata document = DocumentMetadata.upload("doc-1", upload("账单 2026-10"), NOW);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(document.getSizeBytes()).isEqualTo("账单 2026-10".getBytes(StandardCharsets.UTF_8).length);
        assertThat(document.getSizeBytes()).isEqualTo(14);
        assertThat(document.getCreatedAt()).isEqualTo(NOW);
        assertThat(document.getVersion()).isZero();
    }

    @Test
    void archiveMovesActiveToArchivedAndIsIdempotent() {
        DocumentMetadata document = DocumentMetadata.upload("doc-1", upload("hello"), NOW);

        document.archive();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);

        document.archive();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);
    }

    @Test
    void rejectsMissingFileName() {
        assertThatThrownBy(() -> DocumentMetadata.upload("doc-1",
                new DocumentUpload(DocumentType.STATEMENT, OwnerType.CUSTOMER, "000000001",
                        "  ", "text/plain", "hello"), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED")
                .hasMessageContaining("File Name");
    }

    @Test
    void rejectsNonTextContentType() {
        assertThatThrownBy(() -> DocumentMetadata.upload("doc-1",
                new DocumentUpload(DocumentType.CONTRACT, OwnerType.ACCOUNT, "00000000001",
                        "contract.pdf", "application/pdf", "hello"), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED")
                .hasMessageContaining("Content Type");
    }

    @Test
    void rejectsFileNameWithPath() {
        assertThatThrownBy(() -> DocumentMetadata.upload("doc-1",
                new DocumentUpload(DocumentType.RECEIPT, OwnerType.ACCOUNT, "00000000001",
                        "../etc/passwd", "text/plain", "hello"), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    }

    @Test
    void rejectsMissingOwnerTypeAndContentText() {
        assertThatThrownBy(() -> DocumentMetadata.upload("doc-1",
                new DocumentUpload(DocumentType.OTHER, null, "000000001",
                        "note.txt", "text/plain", "hello"), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Owner Type");

        assertThatThrownBy(() -> DocumentMetadata.upload("doc-1",
                new DocumentUpload(DocumentType.OTHER, OwnerType.CUSTOMER, "000000001",
                        "note.txt", "text/plain", null), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Content Text");
    }

    private static DocumentUpload upload(String contentText) {
        return new DocumentUpload(DocumentType.STATEMENT, OwnerType.CUSTOMER, "000000001",
                "statement.txt", "text/plain", contentText);
    }
}
