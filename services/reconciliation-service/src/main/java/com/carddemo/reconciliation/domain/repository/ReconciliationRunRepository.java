package com.carddemo.reconciliation.domain.repository;

import com.carddemo.reconciliation.domain.model.ReconciliationRun;

import java.util.Optional;

public interface ReconciliationRunRepository {

    ReconciliationRun save(ReconciliationRun run);

    Optional<ReconciliationRun> findById(String runId);

    PageResult<ReconciliationRun> search(int page, int size);
}
