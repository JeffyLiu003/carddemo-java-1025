package com.carddemo.reporting.interfaces.rest;

import com.carddemo.reporting.application.ReportService;
import com.carddemo.reporting.domain.model.ReportRun;
import com.carddemo.reporting.domain.repository.PageResult;
import com.carddemo.reporting.interfaces.rest.dto.GenerateReportRequest;
import com.carddemo.reporting.interfaces.rest.dto.ReportRunListResponse;
import com.carddemo.reporting.interfaces.rest.dto.ReportRunResponse;
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

@Tag(name = "报表运行", description = "生成报表（日交易汇总 / 试算平衡）、运行详情与历史")
@RestController
@RequestMapping("/api/v1/report-runs")
public class ReportRunController {

    private final ReportService reports;

    public ReportRunController(ReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "生成报表",
            description = "报表定义必须存在且启用；读取下游→汇总→落 report_run / report_row。"
                    + "下游不可用时本次运行置 FAILED 并返回 503 DEPENDENCY_UNAVAILABLE。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "2026-10-26 日交易汇总",
            value = """
                    {"reportCode":"DAILY_TRANSACTION_SUMMARY","businessDate":"2026-10-26"}
                    """)))
    @PostMapping
    public ResponseEntity<ReportRunResponse> generate(@Valid @RequestBody GenerateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReportRunResponse.from(reports.generate(request.reportCode(), request.businessDate())));
    }

    @Operation(summary = "报表运行详情", description = "运行汇总 + 结果行（按 lineNo 升序）")
    @GetMapping("/{runId}")
    public ReportRunResponse get(@PathVariable String runId) {
        return ReportRunResponse.from(reports.get(runId));
    }

    @Operation(summary = "报表运行历史", description = "按 reportCode 过滤，生成时间倒序分页")
    @GetMapping
    public ReportRunListResponse list(@RequestParam(required = false) String reportCode,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        PageResult<ReportRun> result = reports.listRuns(reportCode, safePage, safeSize);
        return ReportRunListResponse.from(result, safePage, safeSize);
    }
}
