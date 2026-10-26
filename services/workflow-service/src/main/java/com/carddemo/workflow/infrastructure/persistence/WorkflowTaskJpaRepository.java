package com.carddemo.workflow.infrastructure.persistence;

import com.carddemo.workflow.domain.model.WorkflowStatus;
import com.carddemo.workflow.domain.model.WorkflowTask;
import com.carddemo.workflow.domain.repository.TaskPage;
import com.carddemo.workflow.domain.repository.WorkflowTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowTaskJpaRepository extends JpaRepository<WorkflowTask, String>, WorkflowTaskRepository {

    Page<WorkflowTask> findByStatus(WorkflowStatus status, Pageable pageable);

    Page<WorkflowTask> findByAssignee(String assignee, Pageable pageable);

    Page<WorkflowTask> findByStatusAndAssignee(WorkflowStatus status, String assignee, Pageable pageable);

    @Override
    default Optional<WorkflowTask> findByTaskId(String taskId) {
        return findById(taskId);
    }

    @Override
    default TaskPage findPage(WorkflowStatus status, String assignee, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("taskId")));
        Page<WorkflowTask> result;
        if (status != null && assignee != null) {
            result = findByStatusAndAssignee(status, assignee, pageable);
        } else if (status != null) {
            result = findByStatus(status, pageable);
        } else if (assignee != null) {
            result = findByAssignee(assignee, pageable);
        } else {
            result = findAll(pageable);
        }
        return new TaskPage(result.getContent(), page, size, result.getTotalElements());
    }

    @Override
    default WorkflowTask saveTask(WorkflowTask task) {
        return saveAndFlush(task);
    }
}
