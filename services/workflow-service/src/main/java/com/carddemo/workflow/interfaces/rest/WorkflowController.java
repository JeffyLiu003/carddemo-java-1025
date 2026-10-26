package com.carddemo.workflow.interfaces.rest;

import com.carddemo.workflow.application.CreateTaskCommand;
import com.carddemo.workflow.application.WorkflowApplicationService;
import com.carddemo.workflow.domain.model.WorkflowStatus;
import com.carddemo.workflow.domain.model.WorkflowTask;
import com.carddemo.workflow.interfaces.rest.dto.CreateTaskRequest;
import com.carddemo.workflow.interfaces.rest.dto.DecisionRequest;
import com.carddemo.workflow.interfaces.rest.dto.WorkflowDefinitionListResponse;
import com.carddemo.workflow.interfaces.rest.dto.WorkflowTaskPageResponse;
import com.carddemo.workflow.interfaces.rest.dto.WorkflowTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "工作流", description = "流程定义、任务与审批/拒绝（设计 15 §5.2）")
@RestController
@RequestMapping("/api/v1")
@Validated
public class WorkflowController {

    private final WorkflowApplicationService workflowService;

    public WorkflowController(WorkflowApplicationService workflowService) {
        this.workflowService = workflowService;
    }

    @Operation(summary = "流程定义", description = "返回全部定义（含 active 标记），按定义编码升序。")
    @GetMapping("/workflow-definitions")
    public WorkflowDefinitionListResponse definitions() {
        return WorkflowDefinitionListResponse.from(workflowService.definitions());
    }

    @Operation(summary = "建任务", description = "定义必须存在且 active；任务初始状态 PENDING（201）。")
    @PostMapping("/workflow-tasks")
    public ResponseEntity<WorkflowTaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        WorkflowTask task = workflowService.createTask(new CreateTaskCommand(
                request.definitionCode(), request.businessRef(), request.assignee()));
        return ResponseEntity.created(URI.create("/api/v1/workflow-tasks/" + task.getTaskId()))
                .body(WorkflowTaskResponse.from(task));
    }

    @Operation(summary = "任务详情", description = "不存在返回 404。")
    @GetMapping("/workflow-tasks/{taskId}")
    public WorkflowTaskResponse get(@PathVariable String taskId) {
        return WorkflowTaskResponse.from(workflowService.get(taskId));
    }

    @Operation(summary = "任务分页", description = "可按 status 与 assignee 过滤（AND），按创建时间倒序。")
    @GetMapping("/workflow-tasks")
    public WorkflowTaskPageResponse list(
            @RequestParam(required = false)
            WorkflowStatus status,
            @RequestParam(required = false)
            String assignee,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must not be negative.")
            int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Size must be between 1 and 100.")
            @Max(value = 100, message = "Size must be between 1 and 100.")
            int size) {
        return WorkflowTaskPageResponse.from(workflowService.list(status, assignee, page, size));
    }

    @Operation(summary = "审批通过", description = "仅 PENDING 可决策；重复决策 409。comment 可选。")
    @PostMapping("/workflow-tasks/{taskId}/approve")
    public WorkflowTaskResponse approve(@PathVariable String taskId,
                                        @Valid @RequestBody(required = false) DecisionRequest request) {
        return WorkflowTaskResponse.from(workflowService.approve(taskId, commentOf(request)));
    }

    @Operation(summary = "拒绝", description = "仅 PENDING 可决策；重复决策 409。comment 可选。")
    @PostMapping("/workflow-tasks/{taskId}/reject")
    public WorkflowTaskResponse reject(@PathVariable String taskId,
                                       @Valid @RequestBody(required = false) DecisionRequest request) {
        return WorkflowTaskResponse.from(workflowService.reject(taskId, commentOf(request)));
    }

    private static String commentOf(DecisionRequest request) {
        return request == null ? null : request.comment();
    }
}
