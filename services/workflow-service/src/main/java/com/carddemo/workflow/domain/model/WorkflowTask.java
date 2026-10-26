package com.carddemo.workflow.domain.model;

import com.carddemo.workflow.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * 流程任务聚合根（WORKFLOW_TASK）。状态机：PENDING → APPROVED / REJECTED，只允许决策一次。
 * 契约见 docs/design/15-operations-support.md §5。
 */
@Entity
@Table(name = "workflow_task")
public class WorkflowTask {

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String TASK_ALREADY_DECIDED = "TASK_ALREADY_DECIDED";

    @Id
    @Column(name = "task_id", length = 36, nullable = false, updatable = false)
    private String taskId;

    @Column(name = "definition_code", length = 40, nullable = false, updatable = false)
    private String definitionCode;

    @Column(name = "business_ref", length = 64, nullable = false, updatable = false)
    private String businessRef;

    @Column(name = "assignee", length = 64, nullable = false, updatable = false)
    private String assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private WorkflowStatus status;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkflowTask() {
        // for JPA
    }

    /**
     * 建任务：状态 PENDING，decidedAt 为空。
     */
    public static WorkflowTask create(String taskId, String definitionCode, String businessRef,
                                      String assignee, LocalDateTime now) {
        WorkflowTask task = new WorkflowTask();
        task.taskId = requireText(taskId, 36, "Task ID");
        task.definitionCode = requireText(definitionCode, 40, "Definition Code");
        task.businessRef = requireText(businessRef, 64, "Business Reference");
        task.assignee = requireText(assignee, 64, "Assignee");
        task.status = WorkflowStatus.PENDING;
        task.createdAt = now == null ? LocalDateTime.now() : now;
        return task;
    }

    public void approve(String decisionComment, LocalDateTime now) {
        decide(WorkflowStatus.APPROVED, decisionComment, now);
    }

    public void reject(String decisionComment, LocalDateTime now) {
        decide(WorkflowStatus.REJECTED, decisionComment, now);
    }

    /**
     * 仅 PENDING 可决策；重复决策抛 409（TASK_ALREADY_DECIDED）。comment 可选。
     */
    private void decide(WorkflowStatus target, String decisionComment, LocalDateTime now) {
        if (status != WorkflowStatus.PENDING) {
            throw new BusinessRuleException(TASK_ALREADY_DECIDED,
                    "Task " + taskId + " is already " + status + ".");
        }
        this.status = target;
        this.comment = requireComment(decisionComment);
        this.decidedAt = now == null ? LocalDateTime.now() : now;
    }

    private static String requireComment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new BusinessRuleException(VALIDATION_FAILED,
                    "Comment must be 500 characters or less.");
        }
        return trimmed;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(VALIDATION_FAILED, field + " must be supplied.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessRuleException(VALIDATION_FAILED,
                    field + " must be " + maxLength + " characters or less.");
        }
        return trimmed;
    }

    public boolean isPending() {
        return status == WorkflowStatus.PENDING;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDefinitionCode() {
        return definitionCode;
    }

    public String getBusinessRef() {
        return businessRef;
    }

    public String getAssignee() {
        return assignee;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public long getVersion() {
        return version;
    }
}
