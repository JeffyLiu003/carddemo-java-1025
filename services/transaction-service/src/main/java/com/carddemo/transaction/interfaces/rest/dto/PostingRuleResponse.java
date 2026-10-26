package com.carddemo.transaction.interfaces.rest.dto;

import com.carddemo.transaction.domain.model.PostingRule;

/**
 * 过账规则响应（类型详情内嵌）。
 */
public record PostingRuleResponse(
        Long ruleId,
        int legNo,
        String accountRole,
        String direction,
        String ledgerAccountCode,
        String description) {

    public static PostingRuleResponse from(PostingRule rule) {
        return new PostingRuleResponse(rule.getRuleId(), rule.getLegNo(), rule.getAccountRole(),
                rule.getDirection().name(), rule.getLedgerAccountCode(), rule.getDescription());
    }
}
