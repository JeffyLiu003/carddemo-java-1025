package com.carddemo.transaction.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 过账规则（POSTING_RULE）：一个类型下的第 leg_no 条分录模板。唯一 (type_code, leg_no)。
 */
@Entity
@Table(name = "posting_rule")
public class PostingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id", nullable = false, updatable = false)
    private Long ruleId;

    @Column(name = "type_code", length = 20, nullable = false)
    private String typeCode;

    @Column(name = "leg_no", nullable = false)
    private int legNo;

    @Column(name = "account_role", length = 30, nullable = false)
    private String accountRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 2, nullable = false)
    private Direction direction;

    @Column(name = "ledger_account_code", length = 10, nullable = false)
    private String ledgerAccountCode;

    @Column(name = "description", length = 200)
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PostingRule() {
        // for JPA
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getTypeCode() {
        return typeCode;
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

    public String getDescription() {
        return description;
    }

    public long getVersion() {
        return version;
    }
}
