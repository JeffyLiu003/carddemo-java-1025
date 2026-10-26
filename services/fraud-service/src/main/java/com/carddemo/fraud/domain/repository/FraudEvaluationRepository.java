package com.carddemo.fraud.domain.repository;

import com.carddemo.fraud.domain.model.FraudEvaluation;

import java.util.Optional;

/**
 * 评分结果仓储端口（fraud_evaluation）。
 */
public interface FraudEvaluationRepository {

    FraudEvaluation saveEvaluation(FraudEvaluation evaluation);

    Optional<FraudEvaluation> findEvaluation(String evaluationId);
}
