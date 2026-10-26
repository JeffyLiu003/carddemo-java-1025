package com.carddemo.document.interfaces.rest;

import com.carddemo.document.domain.repository.DocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文档端到端用例：H2 + Flyway + MockMvc（设计 15 §4.2）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documents;

    @Test
    void uploadsStatementAndComputesSizeFromUtf8BytesIgnoringRequestSize() throws Exception {
        String body = """
                {
                  "documentType": "STATEMENT",
                  "ownerType": "CUSTOMER",
                  "ownerId": "000000001",
                  "fileName": "statement-2026-10.txt",
                  "contentType": "text/plain",
                  "contentText": "账单 2026-10\\n交易 12 笔",
                  "sizeBytes": 999999
                }
                """;

        String expectedText = "账单 2026-10\n交易 12 笔";
        MvcResult created = mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.documentType").value("STATEMENT"))
                .andExpect(jsonPath("$.ownerType").value("CUSTOMER"))
                .andExpect(jsonPath("$.ownerId").value("000000001"))
                .andExpect(jsonPath("$.fileName").value("statement-2026-10.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.contentText").doesNotExist())
                .andExpect(jsonPath("$.sizeBytes")
                        .value(expectedText.getBytes(StandardCharsets.UTF_8).length))
                .andReturn();

        String documentId = read(created).get("documentId").asText();
        mockMvc.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void uploadReturns201WithLocationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("CONTRACT", "ACCOUNT", "00000000001", "contract.txt", "text/plain",
                                "合同正文")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/documents/")));
    }

    @Test
    void readsContentAsPlainTextInUtf8() throws Exception {
        String documentId = createDocument("RECEIPT", "ACCOUNT", "00000000002", "receipt.txt",
                "收据 100.00 元");

        mockMvc.perform(get("/api/v1/documents/" + documentId + "/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(content().string("收据 100.00 元"));
    }

    @Test
    void archiveMovesActiveToArchivedAndIsIdempotent() throws Exception {
        String documentId = createDocument("STATEMENT", "CUSTOMER", "000000003", "statement.txt", "body");

        mockMvc.perform(post("/api/v1/documents/" + documentId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(post("/api/v1/documents/" + documentId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void deletesMetadataThenGetReturns404() throws Exception {
        String documentId = createDocument("OTHER", "CUSTOMER", "000000004", "note.txt", "body");

        mockMvc.perform(delete("/api/v1/documents/" + documentId))
                .andExpect(status().isNoContent());

        assertThat(documents.findByDocumentId(documentId)).isEmpty();

        mockMvc.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void deleteReturns404ForUnknownDocument() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void getAndContentReturn404ForUnknownDocument() throws Exception {
        mockMvc.perform(get("/api/v1/documents/unknown-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/documents/unknown-id/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void listPagesAndFiltersByOwnerTypeAndOwnerId() throws Exception {
        createDocument("STATEMENT", "CUSTOMER", "000000009", "a.txt", "a");
        createDocument("CONTRACT", "CUSTOMER", "000000009", "b.txt", "b");
        createDocument("RECEIPT", "ACCOUNT", "00000000009", "c.txt", "c");

        mockMvc.perform(get("/api/v1/documents")
                        .param("ownerType", "CUSTOMER")
                        .param("ownerId", "000000009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/documents").param("ownerType", "ACCOUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].documentType").value("RECEIPT"));

        mockMvc.perform(get("/api/v1/documents")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void listReturnsEmptyPageWhenNoDocumentMatches() throws Exception {
        mockMvc.perform(get("/api/v1/documents")
                        .param("ownerType", "CUSTOMER")
                        .param("ownerId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void uploadRejectsUnknownDocumentType() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("INVOICE", "CUSTOMER", "000000001", "x.txt", "text/plain", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void uploadRejectsNonTextContentType() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("STATEMENT", "CUSTOMER", "000000001", "x.pdf",
                                "application/pdf", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value(containsString("contentType")));
    }

    @Test
    void uploadRejectsMissingFileNameAndOwnerId() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"STATEMENT","ownerType":"CUSTOMER",
                                 "ownerId":" ","fileName":"","contentType":"text/plain","contentText":"x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details",
                        hasItems(containsString("ownerId"), containsString("fileName"))));
    }

    @Test
    void listRejectsUnknownOwnerType() throws Exception {
        mockMvc.perform(get("/api/v1/documents").param("ownerType", "MERCHANT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listRejectsOutOfRangePageAndSize() throws Exception {
        mockMvc.perform(get("/api/v1/documents").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/documents").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/documents").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String createDocument(String documentType, String ownerType, String ownerId,
                                  String fileName, String contentText) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(documentType, ownerType, ownerId, fileName, "text/plain",
                                contentText)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("documentId").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String uploadBody(String documentType, String ownerType, String ownerId,
                                     String fileName, String contentType, String contentText) {
        return """
                {"documentType":"%s","ownerType":"%s","ownerId":"%s","fileName":"%s",
                 "contentType":"%s","contentText":"%s"}
                """.formatted(documentType, ownerType, ownerId, fileName, contentType,
                contentText.replace("\n", "\\n").replace("\"", "\\\""));
    }
}
