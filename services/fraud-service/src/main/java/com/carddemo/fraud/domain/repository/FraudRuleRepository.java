package com.carddemo.fraud.domain.repository;

import com.carddemo.fraud.domain.model.FraudRule;

import java.util.List;

/**
 * 评分规则仓储端口（fraud_rule）。
 */
public interface FraudRuleRepository {

    /**
     * 参与评分的规则，按规则编码升序以保证命中顺序稳定。
     */
    List<FraudRule> findActiveRules();
}
