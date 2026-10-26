package com.carddemo.fraud.interfaces.rest.dto;

import com.carddemo.fraud.application.EvaluationOutcome;
import com.carddemo.fraud.domain.model.FraudDecision;
import com.carddemo.fraud.domain.model.FraudEvaluation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评分响应：命中规则与决策；REVIEW/REJECT 时附带生成的警报 id。
 */
public record EvaluationResponse(
        String evaluationId,
        String transactionId,
        String accountId,
        BigDecimal amount,
        String merchantName,
        String country,
        int score,
        FraudDecision decision,
        List<String> matchedRules,
        String alertId,
        LocalDateTime createdAt) {

    public static EvaluationResponse from(EvaluationOutcome outcome) {
        FraudEvaluation evaluation = outcome.evaluation();
        return new EvaluationResponse(
                evaluation.getEvaluationId(),
                evaluation.getTransactionId(),
                evaluation.getAccountId(),
                evaluation.getAmount(),
                evaluation.getMerchantName(),
                evaluation.getCountry(),
                evaluation.getScore(),
                evaluation.getDecision(),
                evaluation.getMatchedRules(),
                outcome.alert() == null ? null : outcome.alert().getAlertId(),
                evaluation.getCreatedAt());
    }
}
