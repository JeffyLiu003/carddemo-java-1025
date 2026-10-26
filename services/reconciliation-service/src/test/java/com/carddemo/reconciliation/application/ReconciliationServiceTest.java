package com.carddemo.reconciliation.application;

import com.carddemo.reconciliation.domain.exception.NotFoundException;
import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import com.carddemo.reconciliation.domain.model.CheckType;
import com.carddemo.reconciliation.domain.model.EodRun;
import com.carddemo.reconciliation.domain.model.ItemStatus;
import com.carddemo.reconciliation.domain.model.ReconciliationItem;
import com.carddemo.reconciliation.domain.model.ReconciliationRun;
import com.carddemo.reconciliation.domain.model.RunStatus;
import com.carddemo.reconciliation.domain.repository.PageResult;
import com.carddemo.reconciliation.infrastructure.client.LedgerClient;
import com.carddemo.reconciliation.infrastructure.client.TransactionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账 / EOD 用例：真实 H2 + Flyway；transaction-service / ledger-service 用 @MockBean 替换。
 * 事务回滚保证用例之间互不影响。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReconciliationServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 26);
    private static final YearMonth PERIOD = YearMonth.of(2026, 10);
    private static final LocalDateTime NOON = BUSINESS_DATE.atTime(12, 0);

    @Autowired
    private ReconciliationService reconciliations;

    @Autowired
    private EodService eods;

    @MockBean
    private TransactionClient transactionClient;

    @MockBean
    private LedgerClient ledgerClient;

    private static TransactionClient.PostingBatch batch(String batchId, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new TransactionClient.PostingBatch(batchId, "CARD_PURCHASE", "00000000006", value, "USD",
                "POSTED", null, NOON,
                List.of(new TransactionClient.PostingLeg("DR", value),
                        new TransactionClient.PostingLeg("CR", value)));
    }

    private static LedgerClient.JournalEntry journal(String entryId, String sourceType, String sourceId,
                                                     String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new LedgerClient.JournalEntry(entryId, sourceType, sourceId, NOON, "POSTED",
                List.of(new LedgerClient.JournalLine("1100", "DR", value),
                        new LedgerClient.JournalLine("2100", "CR", value)));
    }

    private static LedgerClient.JournalEntry postingJournal(String sourceId, String amount) {
        return journal("JE-" + sourceId, "POSTING_BATCH", sourceId, amount);
    }

    private static LedgerClient.TrialBalance trialBalance(String debit, String credit) {
        BigDecimal totalDebit = new BigDecimal(debit);
        BigDecimal totalCredit = new BigDecimal(credit);
        return new LedgerClient.TrialBalance(PERIOD.toString(), totalDebit, totalCredit,
                totalDebit.compareTo(totalCredit) == 0);
    }

    private void stubDownstream(List<TransactionClient.PostingBatch> batches,
                                List<LedgerClient.JournalEntry> journals,
                                LedgerClient.TrialBalance trialBalance) {
        when(transactionClient.findBatches(BUSINESS_DATE)).thenReturn(batches);
        when(ledgerClient.findJournals(BUSINESS_DATE)).thenReturn(journals);
        when(ledgerClient.trialBalance(PERIOD)).thenReturn(trialBalance);
    }

    private static ReconciliationItem itemOf(ReconciliationRunView view, String referenceId) {
        return view.items().stream()
                .filter(item -> item.getReferenceId().equals(referenceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No reconciliation item for " + referenceId));
    }

    @Test
    void matchedRunWhenBatchesAndJournalsAgree() {
        stubDownstream(
                List.of(batch("b-1", "25.00"), batch("b-2", "25.00")),
                List.of(postingJournal("b-1", "25.00"), postingJournal("b-2", "25.00"),
                        journal("JE-MANUAL", "MANUAL", "adj-1", "10.00")),
                trialBalance("50.00", "50.00"));

        ReconciliationRunView view = reconciliations.run(BUSINESS_DATE);
        ReconciliationRun run = view.run();

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.isMatched()).isTrue();
        assertThat(run.getPeriod()).isEqualTo("2026-10");
        assertThat(run.getBatchCount()).isEqualTo(2);
        assertThat(run.getBatchAmount()).isEqualByComparingTo("50.00");
        assertThat(run.getJournalCount()).isEqualTo(2);
        assertThat(run.getJournalDebitTotal()).isEqualByComparingTo("50.00");
        assertThat(run.getJournalCreditTotal()).isEqualByComparingTo("50.00");
        assertThat(run.getMatchCount()).isEqualTo(3);
        assertThat(run.getMismatchCount()).isZero();
        assertThat(run.getMissingJournalCount()).isZero();
        assertThat(run.getMissingBatchCount()).isZero();
        assertThat(run.getCompletedAt()).isNotNull();

        assertThat(view.items()).hasSize(3);
        assertThat(view.items()).extracting(ReconciliationItem::getStatus).containsOnly(ItemStatus.MATCH);
        assertThat(view.items().get(2).getCheckType()).isEqualTo(CheckType.TRIAL_BALANCE);
        assertThat(view.items().get(2).getReferenceId()).isEqualTo("2026-10");

        ReconciliationRunView reloaded = reconciliations.get(run.getRunId());
        assertThat(reloaded.run().getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(reloaded.items()).hasSize(3);

        verify(transactionClient).findBatches(BUSINESS_DATE);
        verify(ledgerClient).trialBalance(PERIOD);
    }

    @Test
    void reportsMissingJournal() {
        stubDownstream(
                List.of(batch("b-1", "25.00"), batch("b-2", "40.00")),
                List.of(postingJournal("b-1", "25.00")),
                trialBalance("25.00", "25.00"));

        ReconciliationRunView view = reconciliations.run(BUSINESS_DATE);

        ReconciliationItem missing = itemOf(view, "b-2");
        assertThat(missing.getStatus()).isEqualTo(ItemStatus.MISSING_JOURNAL);
        assertThat(missing.getExpectedAmount()).isEqualByComparingTo("40.00");
        assertThat(missing.getActualAmount()).isEqualByComparingTo("0.00");
        assertThat(missing.getDifference()).isEqualByComparingTo("40.00");
        assertThat(view.run().isMatched()).isFalse();
        assertThat(view.run().getMismatchCount()).isEqualTo(1);
        assertThat(view.run().getMissingJournalCount()).isEqualTo(1);
        assertThat(view.run().getMatchCount()).isEqualTo(2);
    }

    @Test
    void reportsAmountMismatchWithDifference() {
        stubDownstream(
                List.of(batch("b-1", "25.00")),
                List.of(postingJournal("b-1", "24.00")),
                trialBalance("24.00", "24.00"));

        ReconciliationRunView view = reconciliations.run(BUSINESS_DATE);

        ReconciliationItem mismatch = itemOf(view, "b-1");
        assertThat(mismatch.getStatus()).isEqualTo(ItemStatus.AMOUNT_MISMATCH);
        assertThat(mismatch.getExpectedAmount()).isEqualByComparingTo("25.00");
        assertThat(mismatch.getActualAmount()).isEqualByComparingTo("24.00");
        assertThat(mismatch.getDifference()).isEqualByComparingTo("1.00");
        assertThat(view.run().isMatched()).isFalse();
        assertThat(view.run().getMismatchCount()).isEqualTo(1);
    }

    @Test
    void reportsOrphanJournalAsMissingBatch() {
        stubDownstream(
                List.of(batch("b-1", "25.00")),
                List.of(postingJournal("b-1", "25.00"), journal("JE-9", "POSTING_BATCH", "b-9", "15.00")),
                trialBalance("40.00", "40.00"));

        ReconciliationRunView view = reconciliations.run(BUSINESS_DATE);

        ReconciliationItem orphan = itemOf(view, "b-9");
        assertThat(orphan.getStatus()).isEqualTo(ItemStatus.MISSING_BATCH);
        assertThat(orphan.getExpectedAmount()).isEqualByComparingTo("0.00");
        assertThat(orphan.getActualAmount()).isEqualByComparingTo("15.00");
        assertThat(orphan.getDifference()).isEqualByComparingTo("-15.00");
        assertThat(view.run().getMissingBatchCount()).isEqualTo(1);
        assertThat(view.run().isMatched()).isFalse();
    }

    @Test
    void reportsUnbalancedTrialBalance() {
        stubDownstream(
                List.of(batch("b-1", "25.00")),
                List.of(postingJournal("b-1", "25.00")),
                trialBalance("100.00", "90.00"));

        ReconciliationRunView view = reconciliations.run(BUSINESS_DATE);

        ReconciliationItem trialBalance = itemOf(view, "2026-10");
        assertThat(trialBalance.getCheckType()).isEqualTo(CheckType.TRIAL_BALANCE);
        assertThat(trialBalance.getStatus()).isEqualTo(ItemStatus.UNBALANCED);
        assertThat(trialBalance.getExpectedAmount()).isEqualByComparingTo("100.00");
        assertThat(trialBalance.getActualAmount()).isEqualByComparingTo("90.00");
        assertThat(trialBalance.getDifference()).isEqualByComparingTo("10.00");
        assertThat(view.run().isMatched()).isFalse();
        assertThat(view.run().getMismatchCount()).isEqualTo(1);
    }

    @Test
    void resolveItemStoresNoteAndFlag() {
        stubDownstream(
                List.of(batch("b-1", "25.00")),
                List.of(postingJournal("b-1", "24.00")),
                trialBalance("24.00", "24.00"));
        ReconciliationRunView view = reconciliations.run(BUSINESS_DATE);
        ReconciliationItem mismatch = itemOf(view, "b-1");
        assertThat(mismatch.isResolved()).isFalse();

        ReconciliationItem resolved = reconciliations.resolve(mismatch.getItemId(), "已确认总账同步延迟，手工补录");

        assertThat(resolved.isResolved()).isTrue();
        assertThat(resolved.getResolutionNote()).isEqualTo("已确认总账同步延迟，手工补录");
        assertThat(resolved.getStatus()).isEqualTo(ItemStatus.AMOUNT_MISMATCH);
        ReconciliationItem reloaded = reconciliations.listItems(view.run().getRunId()).stream()
                .filter(item -> item.getItemId().equals(mismatch.getItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(reloaded.isResolved()).isTrue();
        assertThat(reloaded.getResolutionNote()).isEqualTo("已确认总账同步延迟，手工补录");
    }

    @Test
    void unknownRunAndItemAreNotFound() {
        assertThatThrownBy(() -> reconciliations.get("missing-run"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("RECONCILIATION_RUN_NOT_FOUND");
        assertThatThrownBy(() -> reconciliations.resolve("missing-item", "note"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("RECONCILIATION_ITEM_NOT_FOUND");
    }

    @Test
    void downstreamFailureKeepsFailedRunAndThrows() {
        when(transactionClient.findBatches(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        assertThatThrownBy(() -> reconciliations.run(BUSINESS_DATE))
                .isInstanceOf(RemoteServiceException.class);

        PageResult<ReconciliationRun> history = reconciliations.list(0, 10);
        assertThat(history.items()).hasSize(1);
        ReconciliationRun failed = history.items().get(0);
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.isMatched()).isFalse();
        assertThat(failed.getErrorMessage()).contains("transaction-service is unavailable");
        assertThat(failed.getCompletedAt()).isNotNull();
    }

    @Test
    void eodRunCompletesWithReconciliation() {
        stubDownstream(
                List.of(batch("b-1", "25.00")),
                List.of(postingJournal("b-1", "25.00")),
                trialBalance("25.00", "25.00"));

        EodRun eod = eods.start(BUSINESS_DATE);

        assertThat(eod.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(eod.getReconciliationRunId()).isNotBlank();
        assertThat(eod.getMessage()).isEqualTo("Reconciliation matched");
        assertThat(eods.get(eod.getRunId()).getReconciliationRunId()).isEqualTo(eod.getReconciliationRunId());
        assertThat(reconciliations.get(eod.getReconciliationRunId()).run().isMatched()).isTrue();
        assertThat(eods.list(0, 10).items()).hasSize(1);
    }

    @Test
    void eodRunFailsWhenDownstreamUnavailable() {
        when(transactionClient.findBatches(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        assertThatThrownBy(() -> eods.start(BUSINESS_DATE))
                .isInstanceOf(RemoteServiceException.class);

        EodRun failed = eods.list(0, 10).items().get(0);
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.getReconciliationRunId()).isNull();
        assertThat(failed.getMessage()).contains("transaction-service is unavailable");
        assertThat(eods.get(failed.getRunId()).getStatus()).isEqualTo(RunStatus.FAILED);
        assertThatThrownBy(() -> eods.get("missing-eod"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("EOD_RUN_NOT_FOUND");
    }
}
