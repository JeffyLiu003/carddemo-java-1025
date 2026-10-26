package com.carddemo.ledger.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 日记账明细行。行号在分录内唯一，金额恒为正，方向由 {@link Direction} 表达。
 */
@Entity
@Table(name = "journal_line")
public class JournalLine {

    @Id
    @Column(name = "line_id", length = 36, nullable = false, updatable = false)
    private String lineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    private JournalEntry entry;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_code", length = 20, nullable = false)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 2, nullable = false)
    private Direction direction;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    protected JournalLine() {
        // for JPA
    }

    JournalLine(String lineId, JournalEntry entry, int lineNo, String accountCode,
                Direction direction, BigDecimal amount) {
        this.lineId = lineId;
        this.entry = entry;
        this.lineNo = lineNo;
        this.accountCode = accountCode;
        this.direction = direction;
        this.amount = amount;
    }

    public String getLineId() {
        return lineId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
