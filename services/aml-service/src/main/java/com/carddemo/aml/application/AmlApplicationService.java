package com.carddemo.aml.application;

import com.carddemo.aml.domain.exception.InvalidRequestException;
import com.carddemo.aml.domain.exception.NotFoundException;
import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.model.AmlAlertStatus;
import com.carddemo.aml.domain.model.NameNormalizer;
import com.carddemo.aml.domain.model.ScreeningResult;
import com.carddemo.aml.domain.repository.AmlAlertRepository;
import com.carddemo.aml.domain.repository.PageResult;
import com.carddemo.aml.domain.repository.SanctionsEntryRepository;
import com.carddemo.aml.domain.repository.ScreeningResultRepository;
import com.carddemo.aml.domain.service.SanctionsScreeningService;
import com.carddemo.aml.domain.service.ScreeningMatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * AML 用例：名单筛查、筛查查询与警报处置（docs/design/14-risk-credit-collections.md §4）。
 */
@Service
@Transactional
public class AmlApplicationService {

    static final int MAX_PAGE_SIZE = 100;

    private final SanctionsEntryRepository sanctions;
    private final ScreeningResultRepository screenings;
    private final AmlAlertRepository alerts;
    private final SanctionsScreeningService screeningService;
    private final Clock clock;

    public AmlApplicationService(SanctionsEntryRepository sanctions,
                                 ScreeningResultRepository screenings,
                                 AmlAlertRepository alerts,
                                 SanctionsScreeningService screeningService,
                                 Clock clock) {
        this.sanctions = sanctions;
        this.screenings = screenings;
        this.alerts = alerts;
        this.screeningService = screeningService;
        this.clock = clock;
    }

    /**
     * 归一化后比对名单；POTENTIAL_MATCH/BLOCKED 同步生成 OPEN 警报。
     */
    public ScreeningOutcome screen(ScreeningCommand command) {
        String name = command.name() == null ? "" : command.name().trim();
        if (NameNormalizer.normalize(name).isEmpty()) {
            throw new InvalidRequestException("VALIDATION_FAILED",
                    "Name must contain at least one letter or digit.");
        }
        ScreeningMatch match = screeningService.screen(name, sanctions.findActiveEntries());

        LocalDateTime now = LocalDateTime.now(clock);
        ScreeningResult screening = new ScreeningResult(UUID.randomUUID().toString(), name,
                upperOrNull(command.country()), upperOrNull(command.entityType()), match.result(),
                match.matchedEntry() == null ? null : match.matchedEntry().getEntryId(),
                match.score(), now);
        screenings.saveScreening(screening);

        AmlAlert alert = null;
        if (screening.isHit()) {
            alert = alerts.saveAlert(AmlAlert.openFor(UUID.randomUUID().toString(), screening,
                    blankToNull(command.customerId()), now));
        }
        return new ScreeningOutcome(screening, alert);
    }

    @Transactional(readOnly = true)
    public ScreeningResult getScreening(String screeningId) {
        return screenings.findScreening(screeningId)
                .orElseThrow(() -> new NotFoundException("SCREENING_NOT_FOUND",
                        "Screening " + screeningId + " not found"));
    }

    /**
     * 详情附带该筛查生成的警报（若曾生成）。
     */
    @Transactional(readOnly = true)
    public ScreeningOutcome getScreeningDetail(String screeningId) {
        ScreeningResult screening = getScreening(screeningId);
        return new ScreeningOutcome(screening,
                alerts.findAlertByScreening(screeningId).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResult<AmlAlert> listAlerts(String status, int page, int size) {
        validatePage(page, size);
        return alerts.findAlerts(parseStatus(status), page, size);
    }

    public AmlAlert clearAlert(String alertId) {
        AmlAlert alert = getAlert(alertId);
        alert.clear(LocalDateTime.now(clock));
        return alerts.saveAlert(alert);
    }

    public AmlAlert escalateAlert(String alertId) {
        AmlAlert alert = getAlert(alertId);
        alert.escalate(LocalDateTime.now(clock));
        return alerts.saveAlert(alert);
    }

    @Transactional(readOnly = true)
    public AmlAlert getAlert(String alertId) {
        return alerts.findAlert(alertId)
                .orElseThrow(() -> new NotFoundException("ALERT_NOT_FOUND",
                        "AML alert " + alertId + " not found"));
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

    private static AmlAlertStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AmlAlertStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Unknown alert status: " + status);
        }
    }

    private static String upperOrNull(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
