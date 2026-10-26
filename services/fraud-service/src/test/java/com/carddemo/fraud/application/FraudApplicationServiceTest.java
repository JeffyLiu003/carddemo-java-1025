package com.carddemo.fraud.application;

import com.carddemo.fraud.domain.exception.BusinessRuleException;
import com.carddemo.fraud.domain.exception.InvalidRequestException;
import com.carddemo.fraud.domain.exception.NotFoundException;
import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.model.FraudAlertStatus;
import com.carddemo.fraud.domain.model.FraudDecision;
import com.carddemo.fraud.domain.model.FraudEvaluation;
import com.carddemo.fraud.domain.model.FraudRule;
import com.carddemo.fraud.domain.model.FraudRuleType;
import com.carddemo.fraud.domain.repository.FraudAlertRepository;
import com.carddemo.fraud.domain.repository.FraudEvaluationRepository;
import com.carddemo.fraud.domain.repository.FraudRuleRepository;
import com.carddemo.fraud.domain.repository.PageResult;
import com.carddemo.fraud.domain.service.FraudScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评分编排：规则命中 → 决策 → 警报生成与处置（Mockito 隔离仓储）。
 */
@ExtendWith(MockitoExtension.class)
class FraudApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.of(2026, 10, 26, 4, 0);

    private static final List<FraudRule> SEED_RULES = List.of(
            new FraudRule("AMOUNT_GT", "Large amount", FraudRuleType.AMOUNT_GT,
                    new BigDecimal("5000.00"), null, 60, true),
            new FraudRule("MERCHANT_BLOCKED", "Blocked merchant", FraudRuleType.MERCHANT_BLOCKED,
                    null, "BLOCKED MERCHANT", 100, true),
            new FraudRule("COUNTRY_BLOCKED", "Blocked country", FraudRuleType.COUNTRY_BLOCKED,
                    null, "KP,IR", 80, true));

    @Mock
    private FraudRuleRepository rules;

    @Mock
    private FraudEvaluationRepository evaluations;

    @Mock
    private FraudAlertRepository alerts;

    private FraudApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new FraudApplicationService(rules, evaluations, alerts, new FraudScoringService(), clock);
    }

    private void stubRules() {
        when(rules.findActiveRules()).thenReturn(SEED_RULES);
        when(evaluations.saveEvaluation(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(alerts.saveAlert(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static EvaluationCommand command(String amount, String merchantName, String country) {
        return new EvaluationCommand("TXN000000000001", "00000000001",
                new BigDecimal(amount), merchantName, country);
    }

    private static FraudAlert openAlert() {
        FraudEvaluation evaluation = new FraudEvaluation("eval-1", "TXN000000000001", "00000000001",
                new BigDecimal("6000.00"), "Corner Grocery", "US", 60, FraudDecision.REVIEW,
                List.of("AMOUNT_GT"), NOW_LOCAL);
        return FraudAlert.openFor("alert-1", evaluation, NOW_LOCAL);
    }

    @Test
    void reviewsLargeAmountAndOpensAlert() {
        stubRules();

        EvaluationOutcome outcome = service.evaluate(command("6000.00", "Corner Grocery", "us"));

        FraudEvaluation evaluation = outcome.evaluation();
        assertThat(evaluation.getDecision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(evaluation.getScore()).isEqualTo(60);
        assertThat(evaluation.getMatchedRules()).containsExactly("AMOUNT_GT");
        assertThat(evaluation.getCountry()).isEqualTo("US");
        assertThat(evaluation.getCreatedAt()).isEqualTo(NOW_LOCAL);
        assertThat(evaluation.getEvaluationId()).isNotBlank();

        FraudAlert alert = outcome.alert();
        assertThat(alert).isNotNull();
        assertThat(alert.getStatus()).isEqualTo(FraudAlertStatus.OPEN);
        assertThat(alert.getEvaluationId()).isEqualTo(evaluation.getEvaluationId());
        assertThat(alert.getAccountId()).isEqualTo("00000000001");
        assertThat(alert.getScore()).isEqualTo(60);
        assertThat(alert.getReason()).contains("REVIEW", "AMOUNT_GT");
        assertThat(alert.getCreatedAt()).isEqualTo(NOW_LOCAL);
        assertThat(alert.getResolvedAt()).isNull();

        verify(evaluations).saveEvaluation(evaluation);
        verify(alerts).saveAlert(alert);
    }

    @Test
    void approvesLowRiskTransactionWithoutAlert() {
        when(rules.findActiveRules()).thenReturn(SEED_RULES);
        when(evaluations.saveEvaluation(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationOutcome outcome = service.evaluate(command("120.00", "Corner Grocery", "US"));

        assertThat(outcome.evaluation().getDecision()).isEqualTo(FraudDecision.APPROVE);
        assertThat(outcome.evaluation().getScore()).isZero();
        assertThat(outcome.evaluation().getMatchedRules()).isEmpty();
        assertThat(outcome.alert()).isNull();
        verifyNoInteractions(alerts);
    }

    @Test
    void rejectsBlockedMerchantAndListsEveryMatchedRuleInReason() {
        stubRules();

        EvaluationOutcome outcome = service.evaluate(
                command("6000.00", "Blocked Merchant Store", "kp"));

        assertThat(outcome.evaluation().getDecision()).isEqualTo(FraudDecision.REJECT);
        assertThat(outcome.evaluation().getScore()).isEqualTo(240);
        assertThat(outcome.evaluation().getMatchedRules())
                .containsExactly("AMOUNT_GT", "MERCHANT_BLOCKED", "COUNTRY_BLOCKED");

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alerts).saveAlert(captor.capture());
        assertThat(captor.getValue().getReason())
                .contains("REJECT", "AMOUNT_GT", "MERCHANT_BLOCKED", "COUNTRY_BLOCKED");
    }

    @Test
    void confirmsOpenAlert() {
        FraudAlert alert = openAlert();
        when(alerts.findAlert("alert-1")).thenReturn(Optional.of(alert));
        when(alerts.saveAlert(alert)).thenReturn(alert);

        FraudAlert confirmed = service.confirmAlert("alert-1");

        assertThat(confirmed.getStatus()).isEqualTo(FraudAlertStatus.CONFIRMED);
        assertThat(confirmed.getResolvedAt()).isEqualTo(NOW_LOCAL);
        verify(alerts).saveAlert(alert);
    }

    @Test
    void dismissesOpenAlert() {
        FraudAlert alert = openAlert();
        when(alerts.findAlert("alert-1")).thenReturn(Optional.of(alert));
        when(alerts.saveAlert(alert)).thenReturn(alert);

        FraudAlert dismissed = service.dismissAlert("alert-1");

        assertThat(dismissed.getStatus()).isEqualTo(FraudAlertStatus.DISMISSED);
        assertThat(dismissed.getResolvedAt()).isEqualTo(NOW_LOCAL);
    }

    @Test
    void refusesToResolveAlertTwice() {
        FraudAlert alert = openAlert();
        alert.confirm(NOW_LOCAL);
        when(alerts.findAlert("alert-1")).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.dismissAlert("alert-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CONFIRMED")
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("ALERT_ALREADY_RESOLVED");
        verify(alerts, never()).saveAlert(any());
    }

    @Test
    void evaluationDetailIncludesGeneratedAlert() {
        FraudEvaluation evaluation = new FraudEvaluation("eval-1", "TXN000000000001", "00000000001",
                new BigDecimal("6000.00"), "Corner Grocery", "US", 60, FraudDecision.REVIEW,
                List.of("AMOUNT_GT"), NOW_LOCAL);
        FraudAlert alert = FraudAlert.openFor("alert-1", evaluation, NOW_LOCAL);
        when(evaluations.findEvaluation("eval-1")).thenReturn(Optional.of(evaluation));
        when(alerts.findAlertByEvaluation("eval-1")).thenReturn(Optional.of(alert));

        EvaluationOutcome outcome = service.getEvaluationDetail("eval-1");

        assertThat(outcome.evaluation().getDecision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(outcome.alert()).isEqualTo(alert);
    }

    @Test
    void missingEvaluationAndAlertThrowNotFound() {
        when(evaluations.findEvaluation("missing")).thenReturn(Optional.empty());
        when(alerts.findAlert("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEvaluation("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).getCode())
                .isEqualTo("EVALUATION_NOT_FOUND");
        assertThatThrownBy(() -> service.getAlert("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).getCode())
                .isEqualTo("ALERT_NOT_FOUND");
        assertThatThrownBy(() -> service.confirmAlert("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsAlertsWithStatusAndAccountFilters() {
        FraudAlert alert = openAlert();
        when(alerts.findAlerts(FraudAlertStatus.OPEN, "00000000001", 0, 10))
                .thenReturn(new PageResult<>(List.of(alert), 1));

        PageResult<FraudAlert> result = service.listAlerts("open", " 00000000001 ", 0, 10);

        assertThat(result.items()).containsExactly(alert);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidAlertQuery() {
        assertThatThrownBy(() -> service.listAlerts(null, null, -1, 10))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.listAlerts(null, null, 0, 0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.listAlerts(null, null, 0, 101))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.listAlerts("UNKNOWN", null, 0, 10))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.evaluate(command("0.00", "Corner Grocery", "US")))
                .isInstanceOf(InvalidRequestException.class);
    }
}
