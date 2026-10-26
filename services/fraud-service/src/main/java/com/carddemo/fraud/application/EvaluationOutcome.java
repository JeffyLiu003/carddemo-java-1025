package com.carddemo.fraud.application;

import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.model.FraudEvaluation;

/**
 * 评分结果：警报仅在 REVIEW/REJECT 时生成，故可能为 null。
 */
public record EvaluationOutcome(FraudEvaluation evaluation, FraudAlert alert) {
}
