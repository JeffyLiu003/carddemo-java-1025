package com.carddemo.aml.domain.service;

import com.carddemo.aml.domain.model.SanctionsEntry;
import com.carddemo.aml.domain.model.ScreeningResultStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 完全匹配 BLOCKED、双向包含匹配 POTENTIAL_MATCH、否则 CLEAR。
 */
class SanctionsScreeningServiceTest {

    private static final SanctionsEntry ACME = new SanctionsEntry("sanctions-acme-fraud-ltd",
            "ACME FRAUD LTD", "KP", "ENTITY", "OFAC");
    private static final SanctionsEntry BLOCKED_TRADING = new SanctionsEntry("sanctions-blocked-trading",
            "BLOCKED TRADING", "IR", "ENTITY", "OFAC");
    private static final SanctionsEntry IVAN = new SanctionsEntry("sanctions-ivan-petrov",
            "IVAN PETROV", "RU", "INDIVIDUAL", "UN");

    private static final List<SanctionsEntry> ENTRIES = List.of(ACME, BLOCKED_TRADING, IVAN);

    private final SanctionsScreeningService screening = new SanctionsScreeningService();

    @Test
    void exactMatchIsBlocked() {
        ScreeningMatch match = screening.screen("Ivan Petrov", ENTRIES);

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.BLOCKED);
        assertThat(match.matchedEntry()).isEqualTo(IVAN);
        assertThat(match.score()).isEqualTo(100);
    }

    @Test
    void exactMatchIgnoresCaseSpacesAndPunctuation() {
        ScreeningMatch match = screening.screen("  iVaN.   petrov ", ENTRIES);

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.BLOCKED);
        assertThat(match.matchedEntry()).isEqualTo(IVAN);
    }

    @Test
    void recordedNameContainingEntryNameIsPotentialMatch() {
        ScreeningMatch match = screening.screen("ACME FRAUD LTD HOLDINGS", ENTRIES);

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.POTENTIAL_MATCH);
        assertThat(match.matchedEntry()).isEqualTo(ACME);
        assertThat(match.score()).isEqualTo(70);
    }

    @Test
    void entryNameContainingRecordedNameIsPotentialMatch() {
        ScreeningMatch match = screening.screen("Ivan", ENTRIES);

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.POTENTIAL_MATCH);
        assertThat(match.matchedEntry()).isEqualTo(IVAN);
    }

    @Test
    void noMatchIsClear() {
        ScreeningMatch match = screening.screen("John Smith", ENTRIES);

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.CLEAR);
        assertThat(match.matchedEntry()).isNull();
        assertThat(match.score()).isZero();
    }

    @Test
    void prefersExactEntryOverPartialEntry() {
        SanctionsEntry shortEntry = new SanctionsEntry("sanctions-acme", "ACME", "KP", "ENTITY", "OFAC");

        ScreeningMatch match = screening.screen("acme", List.of(ACME, shortEntry));

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.BLOCKED);
        assertThat(match.matchedEntry()).isEqualTo(shortEntry);
    }

    @Test
    void picksLongestPartialMatch() {
        SanctionsEntry shortEntry = new SanctionsEntry("sanctions-acme", "ACME", "KP", "ENTITY", "OFAC");

        ScreeningMatch match = screening.screen("ACME FRAUD LTD HOLDINGS", List.of(shortEntry, ACME));

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.POTENTIAL_MATCH);
        assertThat(match.matchedEntry()).isEqualTo(ACME);
    }

    @Test
    void emptySanctionsListIsClear() {
        ScreeningMatch match = screening.screen("Ivan Petrov", List.of());

        assertThat(match.result()).isEqualTo(ScreeningResultStatus.CLEAR);
    }
}
