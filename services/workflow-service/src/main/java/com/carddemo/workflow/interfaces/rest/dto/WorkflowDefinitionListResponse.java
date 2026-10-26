package com.carddemo.workflow.interfaces.rest.dto;

import com.carddemo.workflow.domain.model.WorkflowDefinition;

import java.util.List;

/**
 * 流程定义列表：{"items": [...]}。
 */
public record WorkflowDefinitionListResponse(List<WorkflowDefinitionResponse> items) {

    public static WorkflowDefinitionListResponse from(List<WorkflowDefinition> definitions) {
        return new WorkflowDefinitionListResponse(
                definitions.stream().map(WorkflowDefinitionResponse::from).toList());
    }
}
