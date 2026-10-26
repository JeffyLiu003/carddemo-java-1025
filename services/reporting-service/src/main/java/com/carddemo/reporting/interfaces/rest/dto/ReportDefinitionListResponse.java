package com.carddemo.reporting.interfaces.rest.dto;

import com.carddemo.reporting.domain.model.ReportDefinition;

import java.util.List;

public record ReportDefinitionListResponse(List<ReportDefinitionResponse> items) {

    public static ReportDefinitionListResponse from(List<ReportDefinition> definitions) {
        return new ReportDefinitionListResponse(
                definitions.stream().map(ReportDefinitionResponse::from).toList());
    }
}
