package com.carddemo.workflow.interfaces.rest.dto;

import com.carddemo.workflow.domain.model.WorkflowDefinition;

import java.util.List;

/**
 * 流程定义响应：steps 以数组返回（库内为逗号分隔的字符串）。
 */
public record WorkflowDefinitionResponse(
        String definitionCode,
        String name,
        List<String> steps,
        boolean active,
        long version) {

    public static WorkflowDefinitionResponse from(WorkflowDefinition definition) {
        return new WorkflowDefinitionResponse(
                definition.getDefinitionCode(),
                definition.getName(),
                definition.stepList(),
                definition.isActive(),
                definition.getVersion());
    }
}
