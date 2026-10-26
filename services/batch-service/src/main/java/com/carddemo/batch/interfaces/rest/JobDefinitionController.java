package com.carddemo.batch.interfaces.rest;

import com.carddemo.batch.application.JobService;
import com.carddemo.batch.interfaces.rest.dto.JobDefinitionListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "作业定义", description = "可执行的 EOD 作业目录（job_definition）")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobDefinitionController {

    private final JobService jobs;

    public JobDefinitionController(JobService jobs) {
        this.jobs = jobs;
    }

    @Operation(summary = "作业定义列表", description = "按 jobCode 升序返回全部作业定义（含停用作业与其 targetService）")
    @GetMapping
    public JobDefinitionListResponse list() {
        return JobDefinitionListResponse.from(jobs.listJobs());
    }
}
