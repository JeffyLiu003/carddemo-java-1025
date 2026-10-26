package com.carddemo.limit.domain.repository;

import com.carddemo.limit.domain.model.LimitDefinition;
import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;

import java.util.Optional;

/**
 * 限额仓储端口。
 */
public interface LimitDefinitionRepository {

    LimitDefinition save(LimitDefinition definition);

    Optional<LimitDefinition> findByLimitId(String limitId);

    /**
     * 悲观锁读取。占用、释放、调整额度时串行化同一限额的并发写入。
     */
    Optional<LimitDefinition> lockByLimitId(String limitId);

    /**
     * 按业务键 (scope_type, scope_id, limit_type, currency) 查找，用于可用性检查。
     */
    Optional<LimitDefinition> findByScope(ScopeType scopeType, String scopeId,
                                          LimitType limitType, String currency);

    PageResult<LimitDefinition> search(ScopeType scopeType, String scopeId, int page, int size);
}
