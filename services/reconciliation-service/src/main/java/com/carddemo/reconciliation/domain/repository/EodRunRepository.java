package com.carddemo.reconciliation.domain.repository;

import com.carddemo.reconciliation.domain.model.EodRun;

import java.util.Optional;

public interface EodRunRepository {

    EodRun save(EodRun run);

    Optional<EodRun> findById(String runId);

    PageResult<EodRun> search(int page, int size);
}
