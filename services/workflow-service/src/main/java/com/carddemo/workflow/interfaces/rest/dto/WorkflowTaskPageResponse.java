package com.carddemo.workflow.interfaces.rest.dto;

import com.carddemo.workflow.domain.repository.TaskPage;

import java.util.List;

/**
 * 分页任务列表：{"items": [...], "page": 0, "size": 10, "total": 2}。
 */
public record WorkflowTaskPageResponse(
        List<WorkflowTaskResponse> items,
        int page,
        int size,
        long total) {

    public static WorkflowTaskPageResponse from(TaskPage taskPage) {
        return new WorkflowTaskPageResponse(
                taskPage.items().stream().map(WorkflowTaskResponse::from).toList(),
                taskPage.page(),
                taskPage.size(),
                taskPage.total());
    }
}
