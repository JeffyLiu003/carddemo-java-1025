package com.carddemo.workflow.domain.model;

/**
 * 任务状态：创建为 PENDING，审批通过 APPROVED，拒绝 REJECTED。
 */
public enum WorkflowStatus {
    PENDING,
    APPROVED,
    REJECTED
}
