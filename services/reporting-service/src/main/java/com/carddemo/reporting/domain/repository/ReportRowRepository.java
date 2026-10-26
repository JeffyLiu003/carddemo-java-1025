package com.carddemo.reporting.domain.repository;

import com.carddemo.reporting.domain.model.ReportRow;

import java.util.List;

public interface ReportRowRepository {

    ReportRow save(ReportRow row);

    List<ReportRow> findByRunId(String runId);
}
