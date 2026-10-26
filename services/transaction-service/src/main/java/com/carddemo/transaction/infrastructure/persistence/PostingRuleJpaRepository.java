package com.carddemo.transaction.infrastructure.persistence;

import com.carddemo.transaction.domain.model.PostingRule;
import com.carddemo.transaction.domain.repository.PostingRuleRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostingRuleJpaRepository
        extends JpaRepository<PostingRule, Long>, PostingRuleRepository {

    @Override
    default List<PostingRule> findRules(String typeCode) {
        return findByTypeCodeOrderByLegNoAsc(typeCode);
    }

    List<PostingRule> findByTypeCodeOrderByLegNoAsc(String typeCode);
}
