package com.carddemo.reporting.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 报表结果行：dimension 为分组维度（卡号 / 科目号 / TOTAL），amount 为汇总金额，detail 为可读补充说明。
 */
@Entity
@Table(name = "report_row")
public class ReportRow {

    private static final int DETAIL_MAX_LENGTH = 200;

    @Id
    @Column(name = "row_id", length = 36, nullable = false, updatable = false)
    private String rowId;

    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @Column(name = "dimension", length = 64, nullable = false, updatable = false)
    private String dimension;

    @Column(name = "amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(name = "detail", length = DETAIL_MAX_LENGTH)
    private String detail;

    protected ReportRow() {
        // for JPA
    }

    private ReportRow(String rowId, String runId, int lineNo, String dimension, BigDecimal amount, String detail) {
        this.rowId = Objects.requireNonNull(rowId, "rowId must not be null");
        this.runId = Objects.requireNonNull(runId, "runId must not be null");
        this.lineNo = lineNo;
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.detail = detail != null && detail.length() > DETAIL_MAX_LENGTH
                ? detail.substring(0, DETAIL_MAX_LENGTH)
                : detail;
    }

    public static ReportRow of(String rowId, String runId, int lineNo, String dimension,
                               BigDecimal amount, String detail) {
        return new ReportRow(rowId, runId, lineNo, dimension, amount, detail);
    }

    public String getRowId() {
        return rowId;
    }

    public String getRunId() {
        return runId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getDimension() {
        return dimension;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDetail() {
        return detail;
    }
}
