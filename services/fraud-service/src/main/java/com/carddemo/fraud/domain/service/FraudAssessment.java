package com.carddemo.fraud.domain.service;

import com.carddemo.fraud.domain.model.FraudDecision;

import java.util.List;

/**
 * 评分结论：命中规则分值之和、命中规则编码与据此得出的决策。
 */
public record FraudAssessment(int score, List<String> matchedRules, FraudDecision decision) {
}
