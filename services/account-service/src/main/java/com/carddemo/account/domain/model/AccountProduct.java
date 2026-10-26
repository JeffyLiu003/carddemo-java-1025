package com.carddemo.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * 账户产品配置。额度上限、费率与账单周期由产品定义，账户只引用产品编码。
 */
@Entity
@Table(name = "account_product")
public class AccountProduct {

    @Id
    @Column(name = "product_code", length = 20, nullable = false, updatable = false)
    private String productCode;

    @Column(name = "name", length = 60, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private ProductCategory category;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "default_credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultCreditLimit;

    @Column(name = "max_credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxCreditLimit;

    @Column(name = "default_cash_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultCashLimit;

    @Column(name = "max_cash_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxCashLimit;

    @Column(name = "apr", nullable = false, precision = 6, scale = 4)
    private BigDecimal apr;

    @Column(name = "monthly_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "statement_cycle_day", nullable = false)
    private int statementCycleDay;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AccountProduct() {
        // for JPA
    }

    public String getProductCode() {
        return productCode;
    }

    public String getName() {
        return name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getDefaultCreditLimit() {
        return defaultCreditLimit;
    }

    public BigDecimal getMaxCreditLimit() {
        return maxCreditLimit;
    }

    public BigDecimal getDefaultCashLimit() {
        return defaultCashLimit;
    }

    public BigDecimal getMaxCashLimit() {
        return maxCashLimit;
    }

    public BigDecimal getApr() {
        return apr;
    }

    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }

    public int getStatementCycleDay() {
        return statementCycleDay;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
