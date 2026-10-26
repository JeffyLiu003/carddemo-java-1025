package com.carddemo.reconciliation.infrastructure.persistence;

import com.carddemo.reconciliation.domain.model.ReconciliationItem;
import com.carddemo.reconciliation.domain.repository.ReconciliationItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationItemJpaRepository
        extends JpaRepository<ReconciliationItem, String>, ReconciliationItemRepository {

    List<ReconciliationItem> findByRunIdOrderByCheckTypeAscReferenceIdAsc(String runId);

    @Override
    default List<ReconciliationItem> findByRunId(String runId) {
        return findByRunIdOrderByCheckTypeAscReferenceIdAsc(runId);
    }
}
