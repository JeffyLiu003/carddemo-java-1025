package com.carddemo.reporting.infrastructure.persistence;

import com.carddemo.reporting.domain.model.ReportRow;
import com.carddemo.reporting.domain.repository.ReportRowRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRowJpaRepository extends JpaRepository<ReportRow, String>, ReportRowRepository {

    List<ReportRow> findByRunIdOrderByLineNoAsc(String runId);

    @Override
    default List<ReportRow> findByRunId(String runId) {
        return findByRunIdOrderByLineNoAsc(runId);
    }
}
