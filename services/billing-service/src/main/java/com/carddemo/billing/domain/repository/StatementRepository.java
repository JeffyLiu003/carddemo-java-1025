package com.carddemo.billing.domain.repository;

import com.carddemo.billing.domain.model.Statement;

import java.util.Optional;

/**
 * 账单仓储端口。
 */
public interface StatementRepository {

    Statement save(Statement statement);

    Optional<Statement> findByStatementId(String statementId);

    Optional<Statement> findByAccountIdAndPeriod(String accountId, String period);

    PageResult<Statement> search(String accountId, int page, int size);
}
