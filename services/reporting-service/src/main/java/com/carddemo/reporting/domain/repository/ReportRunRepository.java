package com.carddemo.reporting.domain.repository;

import com.carddemo.reporting.domain.model.ReportRun;

import java.util.Optional;

public interface ReportRunRepository {

    ReportRun save(ReportRun run);

    Optional<ReportRun> findById(String runId);

    PageResult<ReportRun> search(String reportCode, int page, int size);
}
