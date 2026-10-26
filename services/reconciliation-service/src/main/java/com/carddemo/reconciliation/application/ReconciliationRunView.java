package com.carddemo.reconciliation.application;

import com.carddemo.reconciliation.domain.model.ReconciliationItem;
import com.carddemo.reconciliation.domain.model.ReconciliationRun;

import java.util.List;

/**
 * 对账运行视图：运行汇总 + 该次运行的明细。
 */
public record ReconciliationRunView(ReconciliationRun run, List<ReconciliationItem> items) {
}
