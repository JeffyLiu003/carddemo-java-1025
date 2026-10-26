package com.carddemo.reference.interfaces.rest;

import com.carddemo.reference.application.ReferenceDataApplicationService;
import com.carddemo.reference.interfaces.rest.dto.ReferenceItemRequest;
import com.carddemo.reference.interfaces.rest.dto.ReferenceItemResponse;
import com.carddemo.reference.interfaces.rest.dto.ReferenceItemUpdateRequest;
import com.carddemo.reference.interfaces.rest.dto.ReferenceListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "参考数据", description = "币种/国家/假日/产品类别等码表维护")
@RestController
@RequestMapping("/api/v1/reference")
public class ReferenceController {

    private final ReferenceDataApplicationService referenceData;

    public ReferenceController(ReferenceDataApplicationService referenceData) {
        this.referenceData = referenceData;
    }

    @Operation(summary = "按域列出码表", description = "domain 大写；active 可选过滤（true/false），按 sortOrder、code 升序")
    @GetMapping("/{domain}")
    public ReferenceListResponse list(@PathVariable String domain,
                                      @RequestParam(required = false) Boolean active) {
        return ReferenceListResponse.from(referenceData.list(domain, active));
    }

    @Operation(summary = "码表明细", description = "domain 与 code 均不区分大小写输入，统一大写存储")
    @GetMapping("/{domain}/{code}")
    public ReferenceItemResponse get(@PathVariable String domain, @PathVariable String code) {
        return ReferenceItemResponse.from(referenceData.get(domain, code));
    }

    @Operation(summary = "新增码表项", description = "domain/code 唯一，重复新增返回 409")
    @PostMapping
    public ResponseEntity<ReferenceItemResponse> create(@Valid @RequestBody ReferenceItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReferenceItemResponse.from(referenceData.create(request.toDraft())));
    }

    @Operation(summary = "修改码表项", description = "只允许改 label/sortOrder/active")
    @PutMapping("/{itemId}")
    public ReferenceItemResponse update(@PathVariable String itemId,
                                        @Valid @RequestBody ReferenceItemUpdateRequest request) {
        return ReferenceItemResponse.from(referenceData.update(itemId, request.toUpdate()));
    }

    @Operation(summary = "删除码表项", description = "物理删除；不存在返回 404")
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable String itemId) {
        referenceData.delete(itemId);
        return ResponseEntity.noContent().build();
    }
}
