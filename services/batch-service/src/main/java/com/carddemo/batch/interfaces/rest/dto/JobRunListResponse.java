package com.carddemo.batch.interfaces.rest.dto;

import com.carddemo.batch.domain.model.JobRun;
import com.carddemo.batch.domain.repository.PageResult;

import java.util.List;

public record JobRunListResponse(
        List<JobRunResponse> items,
        int page,
        int size,
        long total) {

    public static JobRunListResponse from(PageResult<JobRun> result, int page, int size) {
        return new JobRunListResponse(
                result.items().stream().map(JobRunResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
