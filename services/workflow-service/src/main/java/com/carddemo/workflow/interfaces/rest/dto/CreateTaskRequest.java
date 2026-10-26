package com.carddemo.workflow.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 建任务请求：definitionCode 必须对应已存在的 active 定义，businessRef/assignee 必填。
 */
public record CreateTaskRequest(
        @NotBlank(message = "Definition Code must be supplied.")
        @Size(max = 40, message = "Definition Code must be 40 characters or less.")
        String definitionCode,

        @NotBlank(message = "Business Reference must be supplied.")
        @Size(max = 64, message = "Business Reference must be 64 characters or less.")
        String businessRef,

        @NotBlank(message = "Assignee must be supplied.")
        @Size(max = 64, message = "Assignee must be 64 characters or less.")
        String assignee) {
}
