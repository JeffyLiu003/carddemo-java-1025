package com.carddemo.batch.interfaces.rest.dto;

import com.carddemo.batch.domain.model.JobDefinition;

import java.util.List;

public record JobDefinitionListResponse(List<JobDefinitionResponse> items) {

    public static JobDefinitionListResponse from(List<JobDefinition> definitions) {
        return new JobDefinitionListResponse(definitions.stream().map(JobDefinitionResponse::from).toList());
    }
}
