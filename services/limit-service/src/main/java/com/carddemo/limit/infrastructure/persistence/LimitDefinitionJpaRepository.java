package com.carddemo.limit.infrastructure.persistence;

import com.carddemo.limit.domain.exception.BusinessRuleException;
import com.carddemo.limit.domain.model.LimitDefinition;
import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;
import com.carddemo.limit.domain.repository.LimitDefinitionRepository;
import com.carddemo.limit.domain.repository.PageResult;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

import java.util.Optional;

public interface LimitDefinitionJpaRepository
        extends JpaRepository<LimitDefinition, String>, LimitDefinitionRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LimitDefinition l where l.limitId = :limitId")
    Optional<LimitDefinition> lockByLimitIdQuery(@Param("limitId") String limitId);

    @Override
    default Optional<LimitDefinition> lockByLimitId(String limitId) {
        return lockByLimitIdQuery(limitId);
    }

    Optional<LimitDefinition> findByScopeTypeAndScopeIdAndLimitTypeAndCurrency(
            ScopeType scopeType, String scopeId, LimitType limitType, String currency);

    @Override
    default Optional<LimitDefinition> findByScope(ScopeType scopeType, String scopeId,
                                                  LimitType limitType, String currency) {
        return findByScopeTypeAndScopeIdAndLimitTypeAndCurrency(scopeType, scopeId, limitType, currency);
    }

    /**
     * 唯一索引 (scope_type, scope_id, limit_type, currency) 兜底并发下的重复创建。
     */
    @Override
    default LimitDefinition save(LimitDefinition definition) {
        try {
            return saveAndFlush(definition);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("LIMIT_ALREADY_DEFINED",
                    "Limit for " + definition.getScopeType() + "/" + definition.getScopeId() + "/"
                            + definition.getLimitType() + "/" + definition.getCurrency()
                            + " already exists.");
        }
    }

    Page<LimitDefinition> findByScopeType(ScopeType scopeType, Pageable pageable);

    Page<LimitDefinition> findByScopeId(String scopeId, Pageable pageable);

    Page<LimitDefinition> findByScopeTypeAndScopeId(ScopeType scopeType, String scopeId, Pageable pageable);

    @Override
    default PageResult<LimitDefinition> search(ScopeType scopeType, String scopeId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Direction.ASC, "scopeType")
                        .and(Sort.by(Sort.Direction.ASC, "scopeId"))
                        .and(Sort.by(Sort.Direction.ASC, "limitType"))
                        .and(Sort.by(Sort.Direction.ASC, "currency")));
        Page<LimitDefinition> result;
        if (scopeType != null && StringUtils.hasText(scopeId)) {
            result = findByScopeTypeAndScopeId(scopeType, scopeId.trim(), pageable);
        } else if (scopeType != null) {
            result = findByScopeType(scopeType, pageable);
        } else if (StringUtils.hasText(scopeId)) {
            result = findByScopeId(scopeId.trim(), pageable);
        } else {
            result = findAll(pageable);
        }
        return new PageResult<>(result.getContent(), pageable.getPageNumber(), pageable.getPageSize(),
                result.getTotalElements());
    }
}
