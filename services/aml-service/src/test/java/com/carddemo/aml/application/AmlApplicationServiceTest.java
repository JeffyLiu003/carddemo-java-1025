package com.carddemo.aml.application;

import com.carddemo.aml.domain.exception.BusinessRuleException;
import com.carddemo.aml.domain.exception.InvalidRequestException;
import com.carddemo.aml.domain.exception.NotFoundException;
import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.model.AmlAlertStatus;
import com.carddemo.aml.domain.model.SanctionsEntry;
import com.carddemo.aml.domain.model.ScreeningResult;
import com.carddemo.aml.domain.model.ScreeningResultStatus;
import com.carddemo.aml.domain.repository.AmlAlertRepository;
import com.carddemo.aml.domain.repository.PageResult;
import com.carddemo.aml.domain.repository.SanctionsEntryRepository;
import com.carddemo.aml.domain.repository.ScreeningResultRepository;
import com.carddemo.aml.domain.service.SanctionsScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 筛查编排：命中 → 警报生成与处置（Mockito 隔离仓储）。
 */
@ExtendWith(MockitoExtension.class)
class AmlApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.of(2026, 10, 26, 4, 0);

    private static final List<SanctionsEntry> ENTRIES = List.of(
            new SanctionsEntry("sanctions-acme-fraud-ltd", "ACME FRAUD LTD", "KP", "ENTITY", "OFAC"),
            new SanctionsEntry("sanctions-blocked-trading", "BLOCKED TRADING", "IR", "ENTITY", "OFAC"),
            new SanctionsEntry("sanctions-ivan-petrov", "IVAN PETROV", "RU", "INDIVIDUAL", "UN"));

    @Mock
    private SanctionsEntryRepository sanctions;

    @Mock
    private ScreeningResultRepository screenings;

    @Mock
    private AmlAlertRepository alerts;

    private AmlApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AmlApplicationService(sanctions, screenings, alerts,
                new SanctionsScreeningService(), clock);
    }

    private void stubEntries() {
        when(sanctions.findActiveEntries()).thenReturn(ENTRIES);
        when(screenings.saveScreening(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(alerts.saveAlert(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ScreeningCommand command(String name, String country, String entityType,
                                            String customerId) {
        return new ScreeningCommand(name, country, entityType, customerId);
    }

    private static ScreeningResult blockedScreening() {
        return new ScreeningResult("screening-1", "Ivan Petrov", "RU", "INDIVIDUAL",
                ScreeningResultStatus.BLOCKED, "sanctions-ivan-petrov", 100, NOW_LOCAL);
    }

    private static AmlAlert openAlert() {
        return AmlAlert.openFor("alert-1", blockedScreening(), "000000001", NOW_LOCAL);
    }

    @Test
    void blockedScreeningOpensAlert() {
        stubEntries();

        ScreeningOutcome outcome = service.screen(command("Ivan Petrov", "ru", "individual", "000000001"));

        ScreeningResult screening = outcome.screening();
        assertThat(screening.getResult()).isEqualTo(ScreeningResultStatus.BLOCKED);
        assertThat(screening.getMatchedEntryId()).isEqualTo("sanctions-ivan-petrov");
        assertThat(screening.getScore()).isEqualTo(100);
        assertThat(screening.getCountry()).isEqualTo("RU");
        assertThat(screening.getEntityType()).isEqualTo("INDIVIDUAL");
        assertThat(screening.getCreatedAt()).isEqualTo(NOW_LOCAL);
        assertThat(screening.getScreeningId()).isNotBlank();

        AmlAlert alert = outcome.alert();
        assertThat(alert).isNotNull();
        assertThat(alert.getStatus()).isEqualTo(AmlAlertStatus.OPEN);
        assertThat(alert.getScreeningId()).isEqualTo(screening.getScreeningId());
        assertThat(alert.getCustomerId()).isEqualTo("000000001");
        assertThat(alert.getDetails()).contains("BLOCKED", "sanctions-ivan-petrov", "100");
        assertThat(alert.getCreatedAt()).isEqualTo(NOW_LOCAL);
        assertThat(alert.getResolvedAt()).isNull();

        verify(screenings).saveScreening(screening);
        verify(alerts).saveAlert(alert);
    }

    @Test
    void potentialMatchOpensAlert() {
        stubEntries();

        ScreeningOutcome outcome = service.screen(
                command("ACME FRAUD LTD HOLDINGS", "KP", "ENTITY", null));

        assertThat(outcome.screening().getResult()).isEqualTo(ScreeningResultStatus.POTENTIAL_MATCH);
        assertThat(outcome.screening().getScore()).isEqualTo(70);
        assertThat(outcome.alert()).isNotNull();
        assertThat(outcome.alert().getCustomerId()).isNull();
        assertThat(outcome.alert().getDetails()).contains("POTENTIAL_MATCH", "sanctions-acme-fraud-ltd");
    }

    @Test
    void clearScreeningDoesNotOpenAlert() {
        when(sanctions.findActiveEntries()).thenReturn(ENTRIES);
        when(screenings.saveScreening(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScreeningOutcome outcome = service.screen(command("John Smith", "US", "INDIVIDUAL", null));

        assertThat(outcome.screening().getResult()).isEqualTo(ScreeningResultStatus.CLEAR);
        assertThat(outcome.screening().getMatchedEntryId()).isNull();
        assertThat(outcome.screening().getScore()).isZero();
        assertThat(outcome.alert()).isNull();
        verifyNoInteractions(alerts);
    }

    @Test
    void rejectsNameWithoutLettersOrDigits() {
        assertThatThrownBy(() -> service.screen(command(" ... --- ", null, null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("letter or digit");
        verifyNoInteractions(sanctions, screenings, alerts);
    }

    @Test
    void clearsOpenAlert() {
        AmlAlert alert = openAlert();
        when(alerts.findAlert("alert-1")).thenReturn(Optional.of(alert));
        when(alerts.saveAlert(alert)).thenReturn(alert);

        AmlAlert cleared = service.clearAlert("alert-1");

        assertThat(cleared.getStatus()).isEqualTo(AmlAlertStatus.CLEARED);
        assertThat(cleared.getResolvedAt()).isEqualTo(NOW_LOCAL);
        verify(alerts).saveAlert(alert);
    }

    @Test
    void escalatesOpenAlert() {
        AmlAlert alert = openAlert();
        when(alerts.findAlert("alert-1")).thenReturn(Optional.of(alert));
        when(alerts.saveAlert(alert)).thenReturn(alert);

        AmlAlert escalated = service.escalateAlert("alert-1");

        assertThat(escalated.getStatus()).isEqualTo(AmlAlertStatus.ESCALATED);
        assertThat(escalated.getResolvedAt()).isEqualTo(NOW_LOCAL);
    }

    @Test
    void refusesToResolveAlertTwice() {
        AmlAlert alert = openAlert();
        alert.escalate(NOW_LOCAL);
        when(alerts.findAlert("alert-1")).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.clearAlert("alert-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ESCALATED")
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("ALERT_ALREADY_RESOLVED");
        verify(alerts, never()).saveAlert(any());
    }

    @Test
    void screeningDetailIncludesGeneratedAlert() {
        ScreeningResult screening = blockedScreening();
        AmlAlert alert = AmlAlert.openFor("alert-1", screening, "000000001", NOW_LOCAL);
        when(screenings.findScreening("screening-1")).thenReturn(Optional.of(screening));
        when(alerts.findAlertByScreening("screening-1")).thenReturn(Optional.of(alert));

        ScreeningOutcome outcome = service.getScreeningDetail("screening-1");

        assertThat(outcome.screening()).isEqualTo(screening);
        assertThat(outcome.alert()).isEqualTo(alert);
    }

    @Test
    void missingScreeningAndAlertThrowNotFound() {
        when(screenings.findScreening("missing")).thenReturn(Optional.empty());
        when(alerts.findAlert("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScreening("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).getCode())
                .isEqualTo("SCREENING_NOT_FOUND");
        assertThatThrownBy(() -> service.getAlert("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).getCode())
                .isEqualTo("ALERT_NOT_FOUND");
        assertThatThrownBy(() -> service.escalateAlert("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsAlertsWithStatusFilter() {
        AmlAlert alert = openAlert();
        when(alerts.findAlerts(AmlAlertStatus.OPEN, 0, 10))
                .thenReturn(new PageResult<>(List.of(alert), 1));

        PageResult<AmlAlert> result = service.listAlerts(" open ", 0, 10);

        assertThat(result.items()).containsExactly(alert);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidAlertQuery() {
        assertThatThrownBy(() -> service.listAlerts(null, -1, 10))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.listAlerts(null, 0, 0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.listAlerts(null, 0, 101))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.listAlerts("UNKNOWN", 0, 10))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
