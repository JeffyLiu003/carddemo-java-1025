package com.carddemo.reporting.interfaces.rest.dto;

import com.carddemo.reporting.domain.model.ReportRow;

import java.math.BigDecimal;

/**
 * 报表结果行：dimension 为分组维度，amount 为汇总金额，detail 为可读补充说明。
 */
public record ReportRowResponse(String rowId,
                                int lineNo,
                                String dimension,
                                BigDecimal amount,
                                String detail) {

    public static ReportRowResponse from(ReportRow row) {
        return new ReportRowResponse(
                row.getRowId(),
                row.getLineNo(),
                row.getDimension(),
                row.getAmount(),
                row.getDetail());
    }
}
