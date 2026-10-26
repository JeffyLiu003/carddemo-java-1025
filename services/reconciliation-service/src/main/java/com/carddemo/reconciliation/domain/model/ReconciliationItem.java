package com.carddemo.reconciliation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 对账明细。difference 统一表示 期望值 - 实际值：正数表示少记、负数表示多记。
 * 差异项可通过 {@link #resolve(String)} 登记处理说明，刷新后保留（验收 5）。
 */
@Entity
@Table(name = "reconciliation_item")
public class ReconciliationItem {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int TEXT_MAX_LENGTH = 500;

    @Id
    @Column(name = "item_id", length = 36, nullable = false, updatable = false)
    private String itemId;

    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", length = 24, nullable = false, updatable = false)
    private CheckType checkType;

    @Column(name = "reference_id", length = 64, nullable = false, updatable = false)
    private String referenceId;

    @Column(name = "expected_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "difference", nullable = false, precision = 14, scale = 2)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private ItemStatus status;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "resolution_note", length = TEXT_MAX_LENGTH)
    private String resolutionNote;

    @Column(name = "detail", length = TEXT_MAX_LENGTH)
    private String detail;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReconciliationItem() {
        // for JPA
    }

    private ReconciliationItem(String itemId, String runId, CheckType checkType, String referenceId,
                               BigDecimal expectedAmount, BigDecimal actualAmount, ItemStatus status,
                               String detail) {
        this.itemId = itemId;
        this.runId = runId;
        this.checkType = Objects.requireNonNull(checkType, "checkType must not be null");
        this.referenceId = Objects.requireNonNull(referenceId, "referenceId must not be null");
        this.expectedAmount = money(expectedAmount);
        this.actualAmount = money(actualAmount);
        this.difference = this.expectedAmount.subtract(this.actualAmount).setScale(2, RoundingMode.HALF_UP);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.resolved = false;
        this.detail = truncate(detail);
    }

    public static ReconciliationItem of(String itemId, String runId, CheckType checkType, String referenceId,
                                        BigDecimal expectedAmount, BigDecimal actualAmount, ItemStatus status,
                                        String detail) {
        return new ReconciliationItem(itemId, runId, checkType, referenceId, expectedAmount, actualAmount,
                status, detail);
    }

    /**
     * 登记差异处理说明；同一明细重复登记时覆盖说明（保留最新结论）。
     */
    public ReconciliationItem resolve(String note) {
        this.resolved = true;
        this.resolutionNote = truncate(note);
        return this;
    }

    private static BigDecimal money(BigDecimal amount) {
        return (amount == null ? ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > TEXT_MAX_LENGTH ? text.substring(0, TEXT_MAX_LENGTH) : text;
    }

    public String getItemId() {
        return itemId;
    }

    public String getRunId() {
        return runId;
    }

    public CheckType getCheckType() {
        return checkType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public boolean isResolved() {
        return resolved;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getDetail() {
        return detail;
    }

    public long getVersion() {
        return version;
    }
}
