package com.carddemo.workflow.application;

import com.carddemo.workflow.domain.exception.BusinessRuleException;
import com.carddemo.workflow.domain.exception.NotFoundException;
import com.carddemo.workflow.domain.model.WorkflowDefinition;
import com.carddemo.workflow.domain.model.WorkflowStatus;
import com.carddemo.workflow.domain.model.WorkflowTask;
import com.carddemo.workflow.domain.repository.TaskPage;
import com.carddemo.workflow.domain.repository.WorkflowDefinitionRepository;
import com.carddemo.workflow.domain.repository.WorkflowTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 流程用例服务：定义查询、任务创建、详情、分页、审批与拒绝（设计 15 §5.2）。
 */
@Service
@Transactional
public class WorkflowApplicationService {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowTaskRepository tasks;

    public WorkflowApplicationService(WorkflowDefinitionRepository definitions,
                                      WorkflowTaskRepository tasks) {
        this.definitions = definitions;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> definitions() {
        return definitions.findAllDefinitions();
    }

    /**
     * 建任务：定义必须存在且 active，否则 409；状态 PENDING。
     */
    public WorkflowTask createTask(CreateTaskCommand command) {
        WorkflowDefinition definition = definitions.findByCode(command.definitionCode())
                .orElseThrow(() -> new BusinessRuleException("WORKFLOW_DEFINITION_NOT_FOUND",
                        "Workflow definition " + command.definitionCode() + " not found"));
        if (!definition.isActive()) {
            throw new BusinessRuleException("WORKFLOW_DEFINITION_INACTIVE",
                    "Workflow definition " + definition.getDefinitionCode() + " is inactive");
        }
        return tasks.saveTask(WorkflowTask.create(UUID.randomUUID().toString(),
                definition.getDefinitionCode(), command.businessRef(), command.assignee(),
                LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public WorkflowTask get(String taskId) {
        return tasks.findByTaskId(taskId)
                .orElseThrow(() -> new NotFoundException("WORKFLOW_TASK_NOT_FOUND",
                        "Workflow task " + taskId + " not found"));
    }

    @Transactional(readOnly = true)
    public TaskPage list(WorkflowStatus status, String assignee, int page, int size) {
        return tasks.findPage(status, blankToNull(assignee), page, size);
    }

    /**
     * 审批通过：仅 PENDING 可决策，重复决策 409。
     */
    public WorkflowTask approve(String taskId, String comment) {
        WorkflowTask task = get(taskId);
        task.approve(comment, LocalDateTime.now());
        return tasks.saveTask(task);
    }

    /**
     * 拒绝：仅 PENDING 可决策，重复决策 409。
     */
    public WorkflowTask reject(String taskId, String comment) {
        WorkflowTask task = get(taskId);
        task.reject(comment, LocalDateTime.now());
        return tasks.saveTask(task);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
