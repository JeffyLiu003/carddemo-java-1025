package com.carddemo.fraud.domain.service;

import com.carddemo.fraud.domain.model.FraudDecision;
import com.carddemo.fraud.domain.model.FraudEvaluationInput;
import com.carddemo.fraud.domain.model.FraudRule;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯评分领域服务：遍历规则累加命中分值，命中规则顺序即规则编码顺序。
 */
public class FraudScoringService {

    public FraudAssessment assess(FraudEvaluationInput input, List<FraudRule> rules) {
        List<String> matchedRules = new ArrayList<>();
        int score = 0;
        for (FraudRule rule : rules) {
            if (rule.matches(input)) {
                matchedRules.add(rule.getRuleCode());
                score += rule.getScore();
            }
        }
        return new FraudAssessment(score, List.copyOf(matchedRules), FraudDecision.fromScore(score));
    }
}
