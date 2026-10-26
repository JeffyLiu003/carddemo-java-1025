package com.carddemo.reconciliation.interfaces.rest;

import com.carddemo.reconciliation.application.ReconciliationService;
import com.carddemo.reconciliation.domain.model.ReconciliationRun;
import com.carddemo.reconciliation.domain.repository.PageResult;
import com.carddemo.reconciliation.interfaces.rest.dto.ReconciliationItemListResponse;
import com.carddemo.reconciliation.interfaces.rest.dto.ReconciliationRunListResponse;
import com.carddemo.reconciliation.interfaces.rest.dto.ReconciliationRunResponse;
import com.carddemo.reconciliation.interfaces.rest.dto.RunReconciliationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@Tag(name = "对账", description = "过账批次与总账日记账的完整性/金额对账、试算平衡与差异明细")
@RestController
@RequestMapping("/api/v1/reconciliation-runs")
public class ReconciliationRunController {

    private final ReconciliationService reconciliations;

    public ReconciliationRunController(ReconciliationService reconciliations) {
        this.reconciliations = reconciliations;
    }

    @Operation(summary = "执行对账",
            description = "按业务日期读取过账批次与日记账并落 reconciliation_run / item；"
                    + "下游不可用时本次运行置 FAILED 并返回 503 DEPENDENCY_UNAVAILABLE。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "2026-10-26 对账",
            value = """
                    {"businessDate":"2026-10-26"}
                    """)))
    @PostMapping
    public ResponseEntity<ReconciliationRunResponse> run(@Valid @RequestBody RunReconciliationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReconciliationRunResponse.from(reconciliations.run(request.businessDate())));
    }

    @Operation(summary = "对账运行详情", description = "汇总口径 + 差异明细")
    @GetMapping("/{runId}")
    public ReconciliationRunResponse get(@PathVariable String runId) {
        return ReconciliationRunResponse.from(reconciliations.get(runId));
    }

    @Operation(summary = "对账运行历史", description = "按开始时间倒序分页")
    @GetMapping
    public ReconciliationRunListResponse list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        PageResult<ReconciliationRun> result = reconciliations.list(safePage, safeSize);
        return ReconciliationRunListResponse.from(result, safePage, safeSize);
    }

    @Operation(summary = "对账差异明细", description = "按对账类型与参考号排序")
    @GetMapping("/{runId}/items")
    public ReconciliationItemListResponse items(@PathVariable String runId) {
        return ReconciliationItemListResponse.from(reconciliations.listItems(runId));
    }
}
