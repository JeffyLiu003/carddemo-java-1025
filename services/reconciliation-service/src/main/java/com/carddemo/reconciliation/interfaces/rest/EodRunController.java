package com.carddemo.reconciliation.interfaces.rest;

import com.carddemo.reconciliation.application.EodService;
import com.carddemo.reconciliation.domain.model.EodRun;
import com.carddemo.reconciliation.domain.repository.PageResult;
import com.carddemo.reconciliation.interfaces.rest.dto.EodRunListResponse;
import com.carddemo.reconciliation.interfaces.rest.dto.EodRunRequest;
import com.carddemo.reconciliation.interfaces.rest.dto.EodRunResponse;
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

@Tag(name = "EOD", description = "日终作业入口：当前步骤为对账，记录作业结果与关联的对账运行")
@RestController
@RequestMapping("/api/v1/eod-runs")
public class EodRunController {

    private final EodService eods;

    public EodRunController(EodService eods) {
        this.eods = eods;
    }

    @Operation(summary = "执行 EOD",
            description = "创建 RUNNING 记录 → 执行对账 → 成功置 COMPLETED 并回写 reconciliationRunId；"
                    + "对账失败置 FAILED 并返回 503，失败原因可查历史。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "2026-10-26 EOD",
            value = """
                    {"businessDate":"2026-10-26"}
                    """)))
    @PostMapping
    public ResponseEntity<EodRunResponse> run(@Valid @RequestBody EodRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(EodRunResponse.from(eods.start(request.businessDate())));
    }

    @Operation(summary = "EOD 详情")
    @GetMapping("/{runId}")
    public EodRunResponse get(@PathVariable String runId) {
        return EodRunResponse.from(eods.get(runId));
    }

    @Operation(summary = "EOD 历史", description = "按开始时间倒序分页")
    @GetMapping
    public EodRunListResponse list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        PageResult<EodRun> result = eods.list(safePage, safeSize);
        return EodRunListResponse.from(result, safePage, safeSize);
    }
}
