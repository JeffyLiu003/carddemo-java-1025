package com.carddemo.transaction.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 过账类型（POSTING_TYPE）。每种类型绑定一组过账规则，并声明对账户余额的影响方向（契约 §2.2）。
 */
@Entity
@Table(name = "posting_type")
public class PostingType {

    @Id
    @Column(name = "type_code", length = 20, nullable = false, updatable = false)
    private String typeCode;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "description", length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_effect", length = 10, nullable = false)
    private AccountEffect accountEffect;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PostingType() {
        // for JPA
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public AccountEffect getAccountEffect() {
        return accountEffect;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
