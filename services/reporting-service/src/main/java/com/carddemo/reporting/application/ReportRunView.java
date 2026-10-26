package com.carddemo.reporting.application;

import com.carddemo.reporting.domain.model.ReportRow;
import com.carddemo.reporting.domain.model.ReportRun;

import java.util.List;

/**
 * 报表运行视图：运行汇总 + 该次运行的结果行。
 */
public record ReportRunView(ReportRun run, List<ReportRow> rows) {
}
