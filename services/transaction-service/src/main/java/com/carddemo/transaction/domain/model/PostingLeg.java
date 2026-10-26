package com.carddemo.transaction.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 过账分录（POSTING_LEG）。属于某个批次的单边，唯一 (batch_id, leg_no)。
 */
@Entity
@Table(name = "posting_leg")
public class PostingLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leg_id", nullable = false, updatable = false)
    private Long legId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private PostingBatch batch;

    @Column(name = "leg_no", nullable = false)
    private int legNo;

    @Column(name = "account_role", length = 30, nullable = false)
    private String accountRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 2, nullable = false)
    private Direction direction;

    @Column(name = "ledger_account_code", length = 10, nullable = false)
    private String ledgerAccountCode;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PostingLeg() {
        // for JPA
    }

    PostingLeg(int legNo, String accountRole, Direction direction, String ledgerAccountCode,
               BigDecimal amount, LocalDateTime createdAt) {
        this.legNo = legNo;
        this.accountRole = accountRole;
        this.direction = direction;
        this.ledgerAccountCode = ledgerAccountCode;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    void attach(PostingBatch batch) {
        this.batch = batch;
    }

    public Long getLegId() {
        return legId;
    }

    public int getLegNo() {
        return legNo;
    }

    public String getAccountRole() {
        return accountRole;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getLedgerAccountCode() {
        return ledgerAccountCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
