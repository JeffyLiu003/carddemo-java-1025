package com.carddemo.workflow.interfaces.rest.dto;

import com.carddemo.workflow.domain.model.WorkflowTask;

import java.time.LocalDateTime;

/**
 * 流程任务响应：PENDING 时 decidedAt 为空。
 */
public record WorkflowTaskResponse(
        String taskId,
        String definitionCode,
        String businessRef,
        String assignee,
        String status,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime decidedAt,
        long version) {

    public static WorkflowTaskResponse from(WorkflowTask task) {
        return new WorkflowTaskResponse(
                task.getTaskId(),
                task.getDefinitionCode(),
                task.getBusinessRef(),
                task.getAssignee(),
                task.getStatus().name(),
                task.getComment(),
                task.getCreatedAt(),
                task.getDecidedAt(),
                task.getVersion());
    }
}
