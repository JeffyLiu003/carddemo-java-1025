package com.carddemo.batch.interfaces.rest.dto;

import com.carddemo.batch.domain.model.JobDefinition;

public record JobDefinitionResponse(
        String jobCode,
        String name,
        String description,
        String targetService,
        boolean active) {

    public static JobDefinitionResponse from(JobDefinition definition) {
        return new JobDefinitionResponse(
                definition.getJobCode(),
                definition.getName(),
                definition.getDescription(),
                definition.getTargetService(),
                definition.isActive());
    }
}
