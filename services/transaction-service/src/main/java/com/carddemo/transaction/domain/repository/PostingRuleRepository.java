package com.carddemo.transaction.domain.repository;

import com.carddemo.transaction.domain.model.PostingRule;

import java.util.List;

/**
 * 过账规则仓储端口：按类型返回按 leg_no 升序的分录模板。
 */
public interface PostingRuleRepository {

    List<PostingRule> findRules(String typeCode);
}
