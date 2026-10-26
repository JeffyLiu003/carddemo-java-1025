package com.carddemo.ledger.domain.model;

import com.carddemo.ledger.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    private static final LocalDateTime POSTED_AT = LocalDateTime.of(2026, 10, 26, 4, 0);
    private static final LocalDateTime CREATED_AT = POSTED_AT.plusMinutes(1);

    private static JournalLineDraft dr(String accountCode, String amount) {
        return new JournalLineDraft(accountCode, Direction.DR, new BigDecimal(amount));
    }

    private static JournalLineDraft cr(String accountCode, String amount) {
        return new JournalLineDraft(accountCode, Direction.CR, new BigDecimal(amount));
    }

    private static JournalEntry entry(JournalLineDraft... lines) {
        return JournalEntry.post("entry-1", "POSTING_BATCH", "batch-1", "Card purchase",
                POSTED_AT, List.of(lines), CREATED_AT);
    }

    @Test
    void postsBalancedEntryWithSequentialLineNumbers() {
        JournalEntry entry = entry(dr("1100", "25.00"), cr("2100", "25.00"));

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(entry.getReversalOf()).isNull();
        assertThat(entry.getSourceType()).isEqualTo("POSTING_BATCH");
        assertThat(entry.getSourceId()).isEqualTo("batch-1");
        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.getLines()).extracting(JournalLine::getLineNo).containsExactly(1, 2);
        assertThat(entry.getLines()).extracting(JournalLine::getAccountCode).containsExactly("1100", "2100");
    }

    @Test
    void rejectsEntryWithLessThanTwoLines() {
        assertThatThrownBy(() -> entry(dr("1100", "25.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("UNBALANCED_JOURNAL");
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> entry(dr("1100", "25.00"), cr("2100", "24.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("UNBALANCED_JOURNAL");
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> entry(dr("1100", "0.00"), cr("2100", "0.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_JOURNAL_AMOUNT");
    }

    @Test
    void reversesBySwappingDirectionsAndMarkingOriginal() {
        JournalEntry entry = entry(dr("1100", "25.00"), cr("2100", "25.00"));

        JournalEntry reversal = entry.reverse("entry-2", POSTED_AT.plusDays(1), CREATED_AT.plusDays(1));

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.REVERSED);
        assertThat(reversal.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(reversal.getSourceType()).isEqualTo("REVERSAL");
        assertThat(reversal.getSourceId()).isEqualTo("entry-1");
        assertThat(reversal.getReversalOf()).isEqualTo("entry-1");
        assertThat(reversal.getLines()).extracting(JournalLine::getDirection)
                .containsExactly(Direction.CR, Direction.DR);
        assertThat(reversal.getLines()).extracting(JournalLine::getAmount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("25.00"));
    }

    @Test
    void reversedEntryCannotBeReversedAgain() {
        JournalEntry entry = entry(dr("1100", "25.00"), cr("2100", "25.00"));
        entry.reverse("entry-2", POSTED_AT.plusDays(1), CREATED_AT.plusDays(1));

        assertThatThrownBy(() -> entry.reverse("entry-3", POSTED_AT.plusDays(2), CREATED_AT.plusDays(2)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("JOURNAL_ALREADY_REVERSED");
    }

    @Test
    void contentComparisonIgnoresLineOrderButNotAmounts() {
        JournalEntry entry = entry(dr("1100", "25.00"), cr("2100", "25.00"));

        assertThat(entry.matchesContent("Card purchase", POSTED_AT,
                List.of(cr("2100", "25.00"), dr("1100", "25.00")))).isTrue();
        assertThat(entry.matchesContent("Card purchase", POSTED_AT,
                List.of(dr("1100", "26.00"), cr("2100", "26.00")))).isFalse();
        assertThat(entry.matchesContent("Other description", POSTED_AT,
                List.of(dr("1100", "25.00"), cr("2100", "25.00")))).isFalse();
    }
}
