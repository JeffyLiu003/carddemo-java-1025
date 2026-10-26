package com.carddemo.batch.interfaces.rest;

import com.carddemo.batch.application.JobService;
import com.carddemo.batch.domain.model.JobRun;
import com.carddemo.batch.domain.repository.PageResult;
import com.carddemo.batch.interfaces.rest.dto.JobRunListResponse;
import com.carddemo.batch.interfaces.rest.dto.JobRunRequest;
import com.carddemo.batch.interfaces.rest.dto.JobRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "作业运行", description = "执行 EOD 作业并查询运行历史；同一 jobCode+businessDate 可重复执行")
@RestController
@RequestMapping("/api/v1/job-runs")
public class JobRunController {

    private final JobService jobs;

    public JobRunController(JobService jobs) {
        this.jobs = jobs;
    }

    @Operation(summary = "执行作业",
            description = "校验作业定义存在且启用 → 落 RUNNING → 调用目标服务 → 成功置 COMPLETED（message 含下游摘要）；"
                    + "下游不可用时置 FAILED 并返回 503 DEPENDENCY_UNAVAILABLE。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "2026-10-26 计息",
            value = """
                    {"jobCode":"INTEREST_RUN","businessDate":"2026-10-26"}
                    """)))
    @PostMapping
    public ResponseEntity<JobRunResponse> execute(@Valid @RequestBody JobRunRequest request) {
        JobRun run = jobs.execute(request.jobCode().trim(), request.businessDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(JobRunResponse.from(run));
    }

    @Operation(summary = "作业运行详情")
    @GetMapping("/{runId}")
    public JobRunResponse get(@PathVariable String runId) {
        return JobRunResponse.from(jobs.getRun(runId));
    }

    @Operation(summary = "作业运行历史",
            description = "可按 jobCode 与 status（RUNNING/COMPLETED/FAILED）过滤，按开始时间倒序分页")
    @GetMapping
    public JobRunListResponse list(@RequestParam(required = false) String jobCode,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        PageResult<JobRun> result = jobs.listRuns(jobCode, status, safePage, safeSize);
        return JobRunListResponse.from(result, safePage, safeSize);
    }
}
