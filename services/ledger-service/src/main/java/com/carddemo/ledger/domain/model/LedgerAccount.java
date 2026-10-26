package com.carddemo.ledger.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 会计科目（COA）。parent_code 表达科目层级；停用科目不允许记账。
 */
@Entity
@Table(name = "ledger_account")
public class LedgerAccount {

    @Id
    @Column(name = "account_code", length = 20, nullable = false, updatable = false)
    private String accountCode;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 16, nullable = false)
    private AccountType accountType;

    @Column(name = "parent_code", length = 20)
    private String parentCode;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LedgerAccount() {
        // for JPA
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getName() {
        return name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getParentCode() {
        return parentCode;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
