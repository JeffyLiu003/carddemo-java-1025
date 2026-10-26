package com.carddemo.billing.infrastructure.persistence;

import com.carddemo.billing.domain.exception.BusinessRuleException;
import com.carddemo.billing.domain.model.Statement;
import com.carddemo.billing.domain.repository.PageResult;
import com.carddemo.billing.domain.repository.StatementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.util.StringUtils;

import java.util.Optional;

public interface StatementJpaRepository extends JpaRepository<Statement, String>, StatementRepository {

    @Override
    Optional<Statement> findByStatementId(String statementId);

    @Override
    Optional<Statement> findByAccountIdAndPeriod(String accountId, String period);

    Page<Statement> findByAccountId(String accountId, Pageable pageable);

    /**
     * 唯一索引 (account_id, period) 兜底并发下同账户+期间的重复写入。
     */
    @Override
    default Statement save(Statement statement) {
        try {
            return saveAndFlush(statement);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("STATEMENT_ALREADY_EXISTS",
                    "Statement for account " + statement.getAccountId() + " period "
                            + statement.getPeriod() + " already generated.");
        }
    }

    @Override
    default PageResult<Statement> search(String accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "period")
                .and(Sort.by(Sort.Direction.DESC, "statementId")));
        Page<Statement> result = StringUtils.hasText(accountId)
                ? findByAccountId(accountId, pageable)
                : findAll(pageable);
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }
}
