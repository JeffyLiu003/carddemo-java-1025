package com.carddemo.fraud.application;

import com.carddemo.fraud.domain.exception.InvalidRequestException;
import com.carddemo.fraud.domain.exception.NotFoundException;
import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.model.FraudAlertStatus;
import com.carddemo.fraud.domain.model.FraudDecision;
import com.carddemo.fraud.domain.model.FraudEvaluation;
import com.carddemo.fraud.domain.model.FraudEvaluationInput;
import com.carddemo.fraud.domain.repository.FraudAlertRepository;
import com.carddemo.fraud.domain.repository.FraudEvaluationRepository;
import com.carddemo.fraud.domain.repository.FraudRuleRepository;
import com.carddemo.fraud.domain.repository.PageResult;
import com.carddemo.fraud.domain.service.FraudAssessment;
import com.carddemo.fraud.domain.service.FraudScoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 欺诈用例：评分、评分查询与警报处置（docs/design/14-risk-credit-collections.md §3）。
 */
@Service
@Transactional
public class FraudApplicationService {

    static final int MAX_PAGE_SIZE = 100;

    private final FraudRuleRepository rules;
    private final FraudEvaluationRepository evaluations;
    private final FraudAlertRepository alerts;
    private final FraudScoringService scoring;
    private final Clock clock;

    public FraudApplicationService(FraudRuleRepository rules,
                                   FraudEvaluationRepository evaluations,
                                   FraudAlertRepository alerts,
                                   FraudScoringService scoring,
                                   Clock clock) {
        this.rules = rules;
        this.evaluations = evaluations;
        this.alerts = alerts;
        this.scoring = scoring;
        this.clock = clock;
    }

    /**
     * 评分并落库；REVIEW/REJECT 同步生成 OPEN 警报。
     */
    public EvaluationOutcome evaluate(EvaluationCommand command) {
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Amount must be greater than zero.");
        }
        String merchantName = blankToNull(command.merchantName());
        String country = upperOrNull(command.country());

        FraudAssessment assessment = scoring.assess(
                new FraudEvaluationInput(command.amount(), merchantName, country), rules.findActiveRules());

        LocalDateTime now = LocalDateTime.now(clock);
        FraudEvaluation evaluation = new FraudEvaluation(UUID.randomUUID().toString(),
                command.transactionId(), command.accountId(), command.amount(), merchantName, country,
                assessment.score(), assessment.decision(), assessment.matchedRules(), now);
        evaluations.saveEvaluation(evaluation);

        FraudAlert alert = null;
        if (assessment.decision() != FraudDecision.APPROVE) {
            alert = alerts.saveAlert(FraudAlert.openFor(UUID.randomUUID().toString(), evaluation, now));
        }
        return new EvaluationOutcome(evaluation, alert);
    }

    @Transactional(readOnly = true)
    public FraudEvaluation getEvaluation(String evaluationId) {
        return evaluations.findEvaluation(evaluationId)
                .orElseThrow(() -> new NotFoundException("EVALUATION_NOT_FOUND",
                        "Fraud evaluation " + evaluationId + " not found"));
    }

    /**
     * 详情附带该评分生成的警报（若曾生成）。
     */
    @Transactional(readOnly = true)
    public EvaluationOutcome getEvaluationDetail(String evaluationId) {
        FraudEvaluation evaluation = getEvaluation(evaluationId);
        return new EvaluationOutcome(evaluation,
                alerts.findAlertByEvaluation(evaluationId).orElse(null));
    }

    @Transactional(readOnly = true)
    public FraudAlert getAlert(String alertId) {
        return alerts.findAlert(alertId)
                .orElseThrow(() -> new NotFoundException("ALERT_NOT_FOUND",
                        "Fraud alert " + alertId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<FraudAlert> listAlerts(String status, String accountId, int page, int size) {
        validatePage(page, size);
        return alerts.findAlerts(parseStatus(status), blankToNull(accountId), page, size);
    }

    public FraudAlert confirmAlert(String alertId) {
        FraudAlert alert = getAlert(alertId);
        alert.confirm(LocalDateTime.now(clock));
        return alerts.saveAlert(alert);
    }

    public FraudAlert dismissAlert(String alertId) {
        FraudAlert alert = getAlert(alertId);
        alert.dismiss(LocalDateTime.now(clock));
        return alerts.saveAlert(alert);
    }

    static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Page must not be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("VALIDATION_FAILED",
                    "Size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    private static FraudAlertStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return FraudAlertStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Unknown alert status: " + status);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upperOrNull(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
