package com.carddemo.fraud.infrastructure.persistence;

import com.carddemo.fraud.domain.model.FraudEvaluation;
import com.carddemo.fraud.domain.repository.FraudEvaluationRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FraudEvaluationJpaRepository
        extends JpaRepository<FraudEvaluation, String>, FraudEvaluationRepository {

    @Override
    default FraudEvaluation saveEvaluation(FraudEvaluation evaluation) {
        return save(evaluation);
    }

    @Override
    default Optional<FraudEvaluation> findEvaluation(String evaluationId) {
        return findById(evaluationId);
    }
}
