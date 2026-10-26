package com.carddemo.billing.application;

import com.carddemo.billing.domain.exception.BusinessRuleException;
import com.carddemo.billing.domain.exception.InvalidRequestException;
import com.carddemo.billing.domain.exception.NotFoundException;
import com.carddemo.billing.domain.model.AccountCycleTotals;
import com.carddemo.billing.domain.model.AccountPosition;
import com.carddemo.billing.domain.model.Statement;
import com.carddemo.billing.domain.repository.PageResult;
import com.carddemo.billing.domain.repository.StatementRepository;
import com.carddemo.billing.infrastructure.client.AccountClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 账单用例：生成（同账户+期间幂等）、详情、分页、全额支付。
 */
@Service
@Transactional
public class StatementService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final Pattern PERIOD_PATTERN = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final StatementRepository statements;
    private final AccountClient accounts;

    public StatementService(StatementRepository statements, AccountClient accounts) {
        this.statements = statements;
        this.accounts = accounts;
    }

    /**
     * 生成账单：同账户+期间已存在时直接返回已有账单（幂等），否则读取账户额度、周期累计与产品 APR 后计算入账。
     */
    public Statement generate(GenerateStatementCommand command) {
        String accountId = requireAccountId(command.accountId());
        YearMonth period = requirePeriod(command.period());

        Optional<Statement> existing = statements.findByAccountIdAndPeriod(accountId, period.toString());
        if (existing.isPresent()) {
            return existing.get();
        }

        AccountPosition position = accounts.creditPosition(accountId);
        AccountCycleTotals cycle = accounts.cycleTotals(accountId);
        String productCode = requireProductCode(position, cycle, accountId);
        Statement statement = Statement.generate(UUID.randomUUID().toString(), accountId, period,
                position.currentBalance(), cycle.currentCycleCredit(), cycle.currentCycleDebit(),
                accounts.findProduct(productCode).apr(), LocalDateTime.now());
        return statements.save(statement);
    }

    /**
     * 全额支付账单：closing 为 0 直接 PAID；否则调用 account-service 还款，成功后置 PAID。
     * 已支付再次调用不重复还款。
     */
    public Statement pay(String statementId) {
        Statement statement = require(statementId);
        if (statement.getStatus().isPaid()) {
            return statement;
        }
        if (statement.amountDue().signum() > 0) {
            accounts.payBill(statement.getAccountId(), statement.amountDue());
        }
        statement.markPaid(LocalDateTime.now());
        return statements.save(statement);
    }

    @Transactional(readOnly = true)
    public Statement get(String statementId) {
        return require(statementId);
    }

    @Transactional(readOnly = true)
    public PageResult<Statement> list(String accountId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        String filter = StringUtils.hasText(accountId) ? accountId.trim() : null;
        return statements.search(filter, Math.max(page, 0), safeSize);
    }

    private Statement require(String statementId) {
        if (!StringUtils.hasText(statementId)) {
            throw new NotFoundException("STATEMENT_NOT_FOUND", "Statement " + statementId + " not found");
        }
        return statements.findByStatementId(statementId.trim())
                .orElseThrow(() -> new NotFoundException("STATEMENT_NOT_FOUND",
                        "Statement " + statementId + " not found"));
    }

    private static String requireAccountId(String accountId) {
        if (!StringUtils.hasText(accountId)) {
            throw new InvalidRequestException("INVALID_ACCOUNT_ID", "Account id must be supplied.");
        }
        return accountId.trim();
    }

    private static YearMonth requirePeriod(String period) {
        if (!StringUtils.hasText(period) || !PERIOD_PATTERN.matcher(period.trim()).matches()) {
            throw new InvalidRequestException("INVALID_PERIOD",
                    "Period " + period + " must be formatted as YYYY-MM.");
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidRequestException("INVALID_PERIOD",
                    "Period " + period + " must be formatted as YYYY-MM.");
        }
    }

    private static String requireProductCode(AccountPosition position, AccountCycleTotals cycle,
                                             String accountId) {
        if (StringUtils.hasText(position.productCode())) {
            return position.productCode();
        }
        if (StringUtils.hasText(cycle.productCode())) {
            return cycle.productCode();
        }
        throw new BusinessRuleException("PRODUCT_NOT_FOUND",
                "Account " + accountId + " has no account product.");
    }
}
