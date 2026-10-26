package com.carddemo.reporting.interfaces.rest;

import com.carddemo.reporting.application.ReportService;
import com.carddemo.reporting.interfaces.rest.dto.ReportDefinitionListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "报表定义", description = "可生成的报表目录（report_definition），含数据来源与启用状态")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportDefinitionController {

    private final ReportService reports;

    public ReportDefinitionController(ReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "报表定义列表", description = "按 reportCode 升序返回全部报表定义（含停用报表与其 sourceService）")
    @GetMapping
    public ReportDefinitionListResponse list() {
        return ReportDefinitionListResponse.from(reports.listDefinitions());
    }
}
