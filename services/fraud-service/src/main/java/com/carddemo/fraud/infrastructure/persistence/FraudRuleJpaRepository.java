package com.carddemo.fraud.infrastructure.persistence;

import com.carddemo.fraud.domain.model.FraudRule;
import com.carddemo.fraud.domain.repository.FraudRuleRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudRuleJpaRepository extends JpaRepository<FraudRule, String>, FraudRuleRepository {

    @Override
    default List<FraudRule> findActiveRules() {
        return findAllByActiveTrueOrderByRuleCodeAsc();
    }

    List<FraudRule> findAllByActiveTrueOrderByRuleCodeAsc();
}
