package com.carddemo.document.interfaces.rest;

import com.carddemo.document.application.DocumentApplicationService;
import com.carddemo.document.domain.model.DocumentMetadata;
import com.carddemo.document.domain.model.DocumentUpload;
import com.carddemo.document.domain.model.OwnerType;
import com.carddemo.document.interfaces.rest.dto.DocumentPageResponse;
import com.carddemo.document.interfaces.rest.dto.DocumentResponse;
import com.carddemo.document.interfaces.rest.dto.UploadDocumentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Tag(name = "文档", description = "文档元数据、文本内容与归档（设计 15 §4.2）")
@RestController
@RequestMapping("/api/v1/documents")
@Validated
public class DocumentController {

    private final DocumentApplicationService documentService;

    public DocumentController(DocumentApplicationService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "上传文档", description = "保存元数据与文本内容（201）；sizeBytes 按文本 UTF-8 字节数计算。")
    @PostMapping
    public ResponseEntity<DocumentResponse> upload(@Valid @RequestBody UploadDocumentRequest request) {
        DocumentUpload upload = new DocumentUpload(request.documentType(), request.ownerType(),
                request.ownerId(), request.fileName(), request.contentType(), request.contentText());
        DocumentMetadata document = documentService.upload(upload);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + document.getDocumentId()))
                .body(DocumentResponse.from(document));
    }

    @Operation(summary = "文档元数据", description = "按 documentId 返回元数据；不存在返回 404。")
    @GetMapping("/{documentId}")
    public DocumentResponse get(@PathVariable String documentId) {
        return DocumentResponse.from(documentService.get(documentId));
    }

    @Operation(summary = "文档内容", description = "返回文本内容（text/plain，UTF-8）。")
    @GetMapping(value = "/{documentId}/content", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> content(@PathVariable String documentId) {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(documentService.content(documentId));
    }

    @Operation(summary = "文档分页", description = "可按 ownerType 与 ownerId 过滤（AND），按创建时间倒序。")
    @GetMapping
    public DocumentPageResponse list(
            @RequestParam(required = false)
            OwnerType ownerType,
            @RequestParam(required = false)
            String ownerId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must not be negative.")
            int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Size must be between 1 and 100.")
            @Max(value = 100, message = "Size must be between 1 and 100.")
            int size) {
        return DocumentPageResponse.from(documentService.list(ownerType, ownerId, page, size));
    }

    @Operation(summary = "归档", description = "ACTIVE → ARCHIVED；重复归档幂等。")
    @PostMapping("/{documentId}/archive")
    public DocumentResponse archive(@PathVariable String documentId) {
        return DocumentResponse.from(documentService.archive(documentId));
    }

    @Operation(summary = "删除元数据", description = "删除成功 204；不存在返回 404。")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        documentService.delete(documentId);
        return ResponseEntity.noContent().build();
    }
}
