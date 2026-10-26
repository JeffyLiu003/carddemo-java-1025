package com.carddemo.workflow.interfaces.rest.dto;

import jakarta.validation.constraints.Size;

/**
 * 审批/拒绝请求体。comment 可选，请求体可整体省略。
 */
public record DecisionRequest(
        @Size(max = 500, message = "Comment must be 500 characters or less.")
        String comment) {
}
