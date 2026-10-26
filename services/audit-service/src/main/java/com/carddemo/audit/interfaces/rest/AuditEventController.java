package com.carddemo.audit.interfaces.rest;

import com.carddemo.audit.application.AuditApplicationService;
import com.carddemo.audit.interfaces.rest.dto.AuditEventPageResponse;
import com.carddemo.audit.interfaces.rest.dto.AuditEventRequest;
import com.carddemo.audit.interfaces.rest.dto.AuditEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "审计事件", description = "追加式审计事件写入与检索（无更新/删除）")
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private final AuditApplicationService audit;

    public AuditEventController(AuditApplicationService audit) {
        this.audit = audit;
    }

    @Operation(summary = "追加审计事件",
            description = "actor/action/resourceType/resourceId 必填，payload 为原始 JSON 文本")
    @PostMapping
    public ResponseEntity<AuditEventResponse> append(@Valid @RequestBody AuditEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuditEventResponse.from(audit.append(request.toDraft())));
    }

    @GetMapping("/{eventId}")
    public AuditEventResponse get(@PathVariable String eventId) {
        return AuditEventResponse.from(audit.get(eventId));
    }

    @Operation(summary = "检索审计事件",
            description = "actor/resourceType/resourceId 可选组合过滤，按 created_at 倒序分页")
    @GetMapping
    public AuditEventPageResponse search(@RequestParam(required = false) String actor,
                                         @RequestParam(required = false) String resourceType,
                                         @RequestParam(required = false) String resourceId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return AuditEventPageResponse.from(audit.search(actor, resourceType, resourceId, page, size));
    }
}
