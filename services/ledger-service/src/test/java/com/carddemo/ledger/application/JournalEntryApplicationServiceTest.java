package com.carddemo.ledger.application;

import com.carddemo.ledger.domain.exception.BusinessRuleException;
import com.carddemo.ledger.domain.exception.NotFoundException;
import com.carddemo.ledger.domain.model.AccountingPeriod;
import com.carddemo.ledger.domain.model.Direction;
import com.carddemo.ledger.domain.model.EntryStatus;
import com.carddemo.ledger.domain.model.JournalEntry;
import com.carddemo.ledger.domain.model.JournalLine;
import com.carddemo.ledger.domain.model.JournalLineDraft;
import com.carddemo.ledger.domain.model.PeriodStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 总账用例：真实 H2 + Flyway 种子，事务回滚保证用例间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JournalEntryApplicationServiceTest {

    private static final LocalDateTime OCT = LocalDateTime.of(2026, 10, 26, 4, 0);
    private static final LocalDateTime NOV = LocalDateTime.of(2026, 11, 10, 9, 0);

    @Autowired
    private JournalEntryApplicationService entries;

    @Autowired
    private TrialBalanceService trialBalances;

    @Autowired
    private AccountingPeriodService accountingPeriods;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String uniqueSourceId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static JournalLineDraft dr(String accountCode, String amount) {
        return new JournalLineDraft(accountCode, Direction.DR, new BigDecimal(amount));
    }

    private static JournalLineDraft cr(String accountCode, String amount) {
        return new JournalLineDraft(accountCode, Direction.CR, new BigDecimal(amount));
    }

    private static PostJournalEntryCommand cardPurchase(String sourceId, LocalDateTime postedAt, String amount) {
        return new PostJournalEntryCommand("POSTING_BATCH", sourceId, "Card purchase", postedAt, "USD",
                List.of(dr("1100", amount), cr("2100", amount)));
    }

    private static TrialBalanceItem item(TrialBalanceView view, String accountCode) {
        return view.items().stream()
                .filter(candidate -> candidate.accountCode().equals(accountCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Trial balance has no item for " + accountCode));
    }

    @Test
    void postsJournalEntryWithLines() {
        JournalEntry entry = entries.post(cardPurchase(uniqueSourceId("post"), OCT, "25.00"));

        assertThat(entry.getEntryId()).isNotBlank();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(entry.getPostedAt()).isEqualTo(OCT);
        assertThat(entry.getLines()).hasSize(2);
        assertThat(entries.get(entry.getEntryId()).getLines()).hasSize(2);
    }

    @Test
    void duplicateSourceWithSameContentReturnsOriginalEntry() {
        String sourceId = uniqueSourceId("idempotent");

        JournalEntry first = entries.post(cardPurchase(sourceId, OCT, "25.00"));
        JournalEntry second = entries.post(cardPurchase(sourceId, OCT, "25.00"));

        assertThat(second.getEntryId()).isEqualTo(first.getEntryId());
        assertThat(entries.list(sourceId, 0, 10).total()).isEqualTo(1);
    }

    @Test
    void duplicateSourceWithDifferentContentConflicts() {
        String sourceId = uniqueSourceId("conflict");
        entries.post(cardPurchase(sourceId, OCT, "25.00"));

        assertThatThrownBy(() -> entries.post(cardPurchase(sourceId, OCT, "26.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("JOURNAL_SOURCE_CONFLICT");
    }

    @Test
    void rejectsUnknownLedgerAccount() {
        PostJournalEntryCommand command = new PostJournalEntryCommand("POSTING_BATCH",
                uniqueSourceId("unknown-account"), "Bad account", OCT, "USD",
                List.of(dr("7777", "10.00"), cr("2100", "10.00")));

        assertThatThrownBy(() -> entries.post(command))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("LEDGER_ACCOUNT_NOT_FOUND");
    }

    @Test
    void rejectsInactiveLedgerAccount() {
        jdbcTemplate.update("update ledger_account set active = false where account_code = '9000'");
        PostJournalEntryCommand command = new PostJournalEntryCommand("POSTING_BATCH",
                uniqueSourceId("inactive-account"), "Suspense posting", OCT, "USD",
                List.of(dr("1100", "10.00"), cr("9000", "10.00")));

        assertThatThrownBy(() -> entries.post(command))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LEDGER_ACCOUNT_INACTIVE");
    }

    @Test
    void rejectsPostingIntoClosedPeriod() {
        PostJournalEntryCommand command = cardPurchase(uniqueSourceId("closed"),
                LocalDateTime.of(2026, 9, 15, 10, 0), "10.00");

        assertThatThrownBy(() -> entries.post(command))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("PERIOD_CLOSED");
    }

    @Test
    void rejectsPostingIntoUnknownPeriod() {
        PostJournalEntryCommand command = cardPurchase(uniqueSourceId("no-period"),
                LocalDateTime.of(2025, 3, 1, 10, 0), "10.00");

        assertThatThrownBy(() -> entries.post(command))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PERIOD_NOT_FOUND");
    }

    @Test
    void trialBalanceAggregatesDebitAndCreditPerAccount() {
        entries.post(cardPurchase(uniqueSourceId("tb-1"), NOV, "25.00"));
        entries.post(cardPurchase(uniqueSourceId("tb-2"), NOV, "15.00"));

        TrialBalanceView view = trialBalances.trialBalance(YearMonth.of(2026, 11));

        assertThat(view.period()).isEqualTo("2026-11");
        assertThat(view.totalDebit()).isEqualByComparingTo("40.00");
        assertThat(view.totalCredit()).isEqualByComparingTo("40.00");
        assertThat(view.balanced()).isTrue();
        assertThat(item(view, "1100").debitTotal()).isEqualByComparingTo("40.00");
        assertThat(item(view, "1100").netAmount()).isEqualByComparingTo("40.00");
        assertThat(item(view, "2100").creditTotal()).isEqualByComparingTo("40.00");
        assertThat(item(view, "2100").netAmount()).isEqualByComparingTo("-40.00");
    }

    @Test
    void reversalSwapsDirectionsAndZeroesNetAmount() {
        JournalEntry original = entries.post(cardPurchase(uniqueSourceId("reversal"), NOV, "25.00"));

        JournalEntry reversal = entries.reverse(original.getEntryId(), NOV.plusDays(1));

        assertThat(reversal.getSourceType()).isEqualTo(JournalEntry.REVERSAL_SOURCE_TYPE);
        assertThat(reversal.getSourceId()).isEqualTo(original.getEntryId());
        assertThat(reversal.getReversalOf()).isEqualTo(original.getEntryId());
        assertThat(reversal.getLines()).extracting(JournalLine::getDirection)
                .containsExactly(Direction.CR, Direction.DR);
        assertThat(entries.get(original.getEntryId()).getStatus()).isEqualTo(EntryStatus.REVERSED);

        TrialBalanceView view = trialBalances.trialBalance(YearMonth.of(2026, 11));

        assertThat(view.totalDebit()).isEqualByComparingTo("50.00");
        assertThat(view.totalCredit()).isEqualByComparingTo("50.00");
        assertThat(view.balanced()).isTrue();
        assertThat(item(view, "1100").netAmount()).isEqualByComparingTo("0.00");
        assertThat(item(view, "2100").netAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void repeatedReversalReturnsExistingReversalEntry() {
        JournalEntry original = entries.post(cardPurchase(uniqueSourceId("reversal-twice"), NOV, "30.00"));

        JournalEntry first = entries.reverse(original.getEntryId(), NOV.plusDays(1));
        JournalEntry second = entries.reverse(original.getEntryId(), NOV.plusDays(2));

        assertThat(second.getEntryId()).isEqualTo(first.getEntryId());
        assertThat(entries.list(original.getEntryId(), 0, 10).total()).isEqualTo(1);
    }

    @Test
    void closePeriodBlocksPostingAndRejectsSecondClose() {
        AccountingPeriod closed = accountingPeriods.close("2026-11");
        assertThat(closed.getStatus()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();

        assertThatThrownBy(() -> accountingPeriods.close("2026-11"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("PERIOD_ALREADY_CLOSED");
        assertThatThrownBy(() -> accountingPeriods.close("2026-12"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PERIOD_NOT_FOUND");
        assertThatThrownBy(() -> entries.post(cardPurchase(uniqueSourceId("after-close"),
                LocalDateTime.of(2026, 11, 20, 10, 0), "10.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("PERIOD_CLOSED");
    }

    @Test
    void unknownEntryIsNotFound() {
        assertThatThrownBy(() -> entries.get("missing-entry"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("JOURNAL_ENTRY_NOT_FOUND");
        assertThatThrownBy(() -> entries.reverse("missing-entry", NOV))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("JOURNAL_ENTRY_NOT_FOUND");
    }
}
