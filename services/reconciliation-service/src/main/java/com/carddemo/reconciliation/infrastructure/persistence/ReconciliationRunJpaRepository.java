package com.carddemo.reconciliation.infrastructure.persistence;

import com.carddemo.reconciliation.domain.model.ReconciliationRun;
import com.carddemo.reconciliation.domain.repository.PageResult;
import com.carddemo.reconciliation.domain.repository.ReconciliationRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunJpaRepository
        extends JpaRepository<ReconciliationRun, String>, ReconciliationRunRepository {

    @Override
    default PageResult<ReconciliationRun> search(int page, int size) {
        Page<ReconciliationRun> result = findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by(Sort.Direction.DESC, "runId"))));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }
}
