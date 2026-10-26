package com.carddemo.workflow.domain.model;

import com.carddemo.workflow.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTaskTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 26, 9, 0);
    private static final LocalDateTime DECIDED = LocalDateTime.of(2026, 10, 26, 11, 30);

    @Test
    void createStartsPendingWithoutDecision() {
        WorkflowTask task = WorkflowTask.create("task-1", "LIMIT_INCREASE", "limit-request-1",
                "ops.user", CREATED);

        assertThat(task.getStatus()).isEqualTo(WorkflowStatus.PENDING);
        assertThat(task.getCreatedAt()).isEqualTo(CREATED);
        assertThat(task.getDecidedAt()).isNull();
        assertThat(task.getComment()).isNull();
        assertThat(task.isPending()).isTrue();
        assertThat(task.getVersion()).isZero();
    }

    @Test
    void approveMovesPendingToApprovedAndRecordsDecidedAt() {
        WorkflowTask task = WorkflowTask.create("task-1", "LIMIT_INCREASE", "ref-1", "ops.user", CREATED);

        task.approve("额度合理", DECIDED);

        assertThat(task.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
        assertThat(task.getComment()).isEqualTo("额度合理");
        assertThat(task.getDecidedAt()).isEqualTo(DECIDED);
        assertThat(task.isPending()).isFalse();
    }

    @Test
    void rejectMovesPendingToRejectedWithOptionalComment() {
        WorkflowTask task = WorkflowTask.create("task-1", "PAYMENT_REVIEW", "ref-1", "ops.user", CREATED);

        task.reject(null, DECIDED);

        assertThat(task.getStatus()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(task.getComment()).isNull();
        assertThat(task.getDecidedAt()).isEqualTo(DECIDED);
    }

    @Test
    void secondDecisionIsRejectedWithConflict() {
        WorkflowTask task = WorkflowTask.create("task-1", "DISPUTE_REVIEW", "ref-1", "ops.user", CREATED);
        task.approve("ok", DECIDED);

        assertThatThrownBy(() -> task.approve("again", DECIDED))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TASK_ALREADY_DECIDED");

        assertThatThrownBy(() -> task.reject("again", DECIDED))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TASK_ALREADY_DECIDED");
    }

    @Test
    void createRequiresBusinessReferenceAndAssignee() {
        assertThatThrownBy(() -> WorkflowTask.create("task-1", "LIMIT_INCREASE", " ", "ops.user", CREATED))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED")
                .hasMessageContaining("Business Reference");

        assertThatThrownBy(() -> WorkflowTask.create("task-1", "LIMIT_INCREASE", "ref-1", null, CREATED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Assignee");
    }

    @Test
    void commentLongerThanFiveHundredCharactersIsRejected() {
        WorkflowTask task = WorkflowTask.create("task-1", "LIMIT_INCREASE", "ref-1", "ops.user", CREATED);

        assertThatThrownBy(() -> task.approve("x".repeat(501), DECIDED))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED")
                .hasMessageContaining("Comment");
    }
}
