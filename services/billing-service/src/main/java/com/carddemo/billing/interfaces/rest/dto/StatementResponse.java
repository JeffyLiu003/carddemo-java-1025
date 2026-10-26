package com.carddemo.billing.interfaces.rest.dto;

import com.carddemo.billing.domain.model.Statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账单响应。status 为只读状态：未支付且已过到期日展示 OVERDUE。
 */
public record StatementResponse(
        String statementId,
        String accountId,
        String period,
        LocalDate cycleDate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal purchases,
        BigDecimal payments,
        BigDecimal fees,
        BigDecimal interest,
        BigDecimal minimumPayment,
        LocalDate dueDate,
        String status,
        LocalDateTime generatedAt,
        LocalDateTime paidAt,
        long version) {

    public static StatementResponse from(Statement statement) {
        return from(statement, LocalDate.now());
    }

    public static StatementResponse from(Statement statement, LocalDate today) {
        return new StatementResponse(
                statement.getStatementId(),
                statement.getAccountId(),
                statement.getPeriod(),
                statement.getCycleDate(),
                statement.getOpeningBalance(),
                statement.getClosingBalance(),
                statement.getPurchases(),
                statement.getPayments(),
                statement.getFees(),
                statement.getInterest(),
                statement.getMinimumPayment(),
                statement.getDueDate(),
                statement.statusOn(today).name(),
                statement.getGeneratedAt(),
                statement.getPaidAt(),
                statement.getVersion());
    }
}
