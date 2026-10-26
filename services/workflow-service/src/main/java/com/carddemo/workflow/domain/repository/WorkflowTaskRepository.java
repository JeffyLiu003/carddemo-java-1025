package com.carddemo.workflow.domain.repository;

import com.carddemo.workflow.domain.model.WorkflowStatus;
import com.carddemo.workflow.domain.model.WorkflowTask;

import java.util.Optional;

/**
 * 流程任务仓储端口。实现见 infrastructure.persistence.WorkflowTaskJpaRepository。
 */
public interface WorkflowTaskRepository {

    Optional<WorkflowTask> findByTaskId(String taskId);

    TaskPage findPage(WorkflowStatus status, String assignee, int page, int size);

    WorkflowTask saveTask(WorkflowTask task);
}
