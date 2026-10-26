package com.carddemo.reporting.interfaces.rest.dto;

import com.carddemo.reporting.domain.model.ReportDefinition;

/**
 * 报表定义响应（GET /api/v1/reports）。
 */
public record ReportDefinitionResponse(String reportCode,
                                       String name,
                                       String description,
                                       String sourceService,
                                       boolean active) {

    public static ReportDefinitionResponse from(ReportDefinition definition) {
        return new ReportDefinitionResponse(
                definition.getReportCode(),
                definition.getName(),
                definition.getDescription(),
                definition.getSourceService(),
                definition.isActive());
    }
}
