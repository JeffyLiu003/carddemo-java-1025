package com.carddemo.ledger.domain.model;

import com.carddemo.ledger.domain.exception.BusinessRuleException;
import com.carddemo.ledger.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 会计期间（YYYY-MM）。关账后该期间不允许再记账；关账可重复调用则报 409。
 */
@Entity
@Table(name = "accounting_period")
public class AccountingPeriod {

    @Id
    @Column(name = "period", length = 7, nullable = false, updatable = false)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PeriodStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AccountingPeriod() {
        // for JPA
    }

    /**
     * 解析期间参数；格式非法属于请求错误（400），不是业务冲突。
     */
    public static YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new InvalidRequestException("INVALID_PERIOD", "Period must be supplied as YYYY-MM.");
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidRequestException("INVALID_PERIOD", "Period must be in YYYY-MM format.");
        }
    }

    /**
     * 新开一个期间（种子与测试使用），默认 OPEN。
     */
    public static AccountingPeriod open(String period) {
        AccountingPeriod accountingPeriod = new AccountingPeriod();
        accountingPeriod.period = parsePeriod(period).toString();
        accountingPeriod.status = PeriodStatus.OPEN;
        return accountingPeriod;
    }

    public void close(LocalDateTime now) {
        if (status == PeriodStatus.CLOSED) {
            throw new BusinessRuleException("PERIOD_ALREADY_CLOSED",
                    "Accounting period " + period + " is already closed.");
        }
        this.status = PeriodStatus.CLOSED;
        this.closedAt = now;
    }

    public boolean isOpen() {
        return status == PeriodStatus.OPEN;
    }

    public YearMonth asYearMonth() {
        return YearMonth.parse(period);
    }

    public String getPeriod() {
        return period;
    }

    public PeriodStatus getStatus() {
        return status;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }
}
