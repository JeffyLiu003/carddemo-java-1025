package com.carddemo.workflow.domain.repository;

import com.carddemo.workflow.domain.model.WorkflowTask;

import java.util.List;

/**
 * 任务分页结果：{"items": [...], "page": 0, "size": 10, "total": 2}。
 */
public record TaskPage(List<WorkflowTask> items, int page, int size, long total) {
}
