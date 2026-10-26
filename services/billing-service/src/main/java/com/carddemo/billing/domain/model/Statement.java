package com.carddemo.billing.domain.model;

import com.carddemo.billing.domain.exception.BusinessRuleException;
import com.carddemo.billing.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 账单聚合根（docs/design/13-payments-billing-limits-fx.md §3）。
 *
 * <p>计算口径：
 * <ul>
 *   <li>closing_balance = 账户 currentBalance；purchases = currentCycleCredit；
 *       payments = abs(min(currentCycleDebit, 0))；opening_balance = closing - purchases + payments；</li>
 *   <li>interest = closing × APR ÷ 12（HALF_UP，2 位），fees = 0；</li>
 *   <li>minimum_payment = closing == 0 ? 0 : min(closing, max(25.00, closing × 3%))；</li>
 *   <li>cycle_date = 期间最后一天，due_date = cycle_date + 25 天。</li>
 * </ul>
 *
 * <p>状态只在 GENERATED / PAID 之间流转；OVERDUE 由 {@link #statusOn(LocalDate)} 按到期日只读判定。
 */
@Entity
@Table(name = "statement")
public class Statement {

    public static final BigDecimal MINIMUM_PAYMENT_FLOOR = new BigDecimal("25.00");
    public static final BigDecimal MINIMUM_PAYMENT_RATE = new BigDecimal("0.03");
    public static final int DUE_DAYS_AFTER_CYCLE = 25;

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");
    private static final int AMOUNT_SCALE = 2;
    private static final int ACCOUNT_ID_MAX_LENGTH = 11;

    @Id
    @Column(name = "statement_id", length = 36, nullable = false, updatable = false)
    private String statementId;

    @Column(name = "account_id", length = ACCOUNT_ID_MAX_LENGTH, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "period", length = 7, nullable = false, updatable = false)
    private String period;

    @Column(name = "cycle_date", nullable = false, updatable = false)
    private LocalDate cycleDate;

    @Column(name = "opening_balance", nullable = false, updatable = false)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false, updatable = false)
    private BigDecimal closingBalance;

    @Column(name = "purchases", nullable = false, updatable = false)
    private BigDecimal purchases;

    @Column(name = "payments", nullable = false, updatable = false)
    private BigDecimal payments;

    @Column(name = "fees", nullable = false, updatable = false)
    private BigDecimal fees;

    @Column(name = "interest", nullable = false, updatable = false)
    private BigDecimal interest;

    @Column(name = "minimum_payment", nullable = false, updatable = false)
    private BigDecimal minimumPayment;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private StatementStatus status;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Statement() {
        // for JPA
    }

    /**
     * 生成账单。金额一律按 2 位 HALF_UP 归一后落库。
     */
    public static Statement generate(String statementId, String accountId, YearMonth period,
                                     BigDecimal currentBalance, BigDecimal currentCycleCredit,
                                     BigDecimal currentCycleDebit, BigDecimal apr,
                                     LocalDateTime generatedAt) {
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Statement statement = new Statement();
        statement.statementId = requireText(statementId, "statementId");
        statement.accountId = requireAccountId(accountId);
        statement.period = period.toString();

        BigDecimal closing = amount(currentBalance);
        BigDecimal purchases = amount(currentCycleCredit);
        BigDecimal cycleDebit = amount(currentCycleDebit);
        BigDecimal payments = cycleDebit.signum() < 0 ? cycleDebit.abs() : ZERO;

        statement.closingBalance = closing;
        statement.purchases = purchases;
        statement.payments = payments;
        statement.openingBalance = closing.subtract(purchases).add(payments);
        statement.fees = ZERO;
        statement.interest = closing.multiply(apr == null ? ZERO : apr)
                .divide(MONTHS_PER_YEAR, AMOUNT_SCALE, RoundingMode.HALF_UP);
        statement.minimumPayment = minimumPaymentOf(closing);
        statement.cycleDate = period.atEndOfMonth();
        statement.dueDate = statement.cycleDate.plusDays(DUE_DAYS_AFTER_CYCLE);
        statement.status = StatementStatus.GENERATED;
        statement.generatedAt = generatedAt;
        return statement;
    }

    /**
     * 标记账单已支付；重复调用幂等，保留首次支付时间。
     */
    public void markPaid(LocalDateTime paidAt) {
        Objects.requireNonNull(paidAt, "paidAt must not be null");
        if (status.isPaid()) {
            return;
        }
        status = StatementStatus.PAID;
        this.paidAt = paidAt;
    }

    /**
     * 应付金额（全额支付）：欠款大于零时等于 closing_balance，否则无需支付。
     */
    public BigDecimal amountDue() {
        return closingBalance.signum() > 0 ? closingBalance : ZERO;
    }

    public boolean requiresPayment() {
        return !status.isPaid() && amountDue().signum() > 0;
    }

    /**
     * 只读状态：未支付且已过到期日展示为 OVERDUE，不落库。
     */
    public StatementStatus statusOn(LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        if (status.isPaid()) {
            return StatementStatus.PAID;
        }
        return dueDate.isBefore(today) ? StatementStatus.OVERDUE : StatementStatus.GENERATED;
    }

    private static BigDecimal minimumPaymentOf(BigDecimal closing) {
        if (closing.signum() <= 0) {
            return ZERO;
        }
        BigDecimal proportional = closing.multiply(MINIMUM_PAYMENT_RATE).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        return proportional.max(MINIMUM_PAYMENT_FLOOR).min(closing);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("INVALID_STATEMENT", "Statement " + field + " must be supplied.");
        }
        return value.trim();
    }

    private static String requireAccountId(String accountId) {
        String value = requireText(accountId, "accountId");
        if (value.length() > ACCOUNT_ID_MAX_LENGTH) {
            throw new InvalidRequestException("INVALID_ACCOUNT_ID",
                    "Statement accountId must not exceed " + ACCOUNT_ID_MAX_LENGTH + " characters.");
        }
        return value;
    }

    public String getStatementId() {
        return statementId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getPeriod() {
        return period;
    }

    public LocalDate getCycleDate() {
        return cycleDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public BigDecimal getPurchases() {
        return purchases;
    }

    public BigDecimal getPayments() {
        return payments;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public BigDecimal getMinimumPayment() {
        return minimumPayment;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public StatementStatus getStatus() {
        return status;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public long getVersion() {
        return version;
    }
}
