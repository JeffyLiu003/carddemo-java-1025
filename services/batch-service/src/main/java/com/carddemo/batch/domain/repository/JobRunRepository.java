package com.carddemo.batch.domain.repository;

import com.carddemo.batch.domain.model.JobRun;

import java.util.Optional;

public interface JobRunRepository {

    JobRun save(JobRun run);

    Optional<JobRun> findById(String runId);

    PageResult<JobRun> search(JobRunFilter filter, int page, int size);
}
