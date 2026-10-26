package com.carddemo.reconciliation.domain.repository;

import com.carddemo.reconciliation.domain.model.ReconciliationItem;

import java.util.List;
import java.util.Optional;

public interface ReconciliationItemRepository {

    ReconciliationItem save(ReconciliationItem item);

    Optional<ReconciliationItem> findById(String itemId);

    /**
     * 某次运行的明细，按对账类型与参考号排序，保证多次查询顺序稳定。
     */
    List<ReconciliationItem> findByRunId(String runId);
}
