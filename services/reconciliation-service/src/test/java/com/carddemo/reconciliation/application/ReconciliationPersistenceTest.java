package com.carddemo.reconciliation.application;

import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import com.carddemo.reconciliation.domain.model.EodRun;
import com.carddemo.reconciliation.domain.model.ItemStatus;
import com.carddemo.reconciliation.domain.model.ReconciliationItem;
import com.carddemo.reconciliation.domain.model.ReconciliationRun;
import com.carddemo.reconciliation.domain.model.RunStatus;
import com.carddemo.reconciliation.infrastructure.client.LedgerClient;
import com.carddemo.reconciliation.infrastructure.client.TransactionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 不使用测试事务：验证 RUNNING → COMPLETED/FAILED 的多次提交在真实事务边界下成立
 * （下游失败时 RUNNING 记录不能被回滚掉）。每个用例结束后清空三张表。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationPersistenceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 3);
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);
    private static final LocalDateTime NOON = BUSINESS_DATE.atTime(12, 0);

    @Autowired
    private ReconciliationService reconciliations;

    @Autowired
    private EodService eods;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TransactionClient transactionClient;

    @MockBean
    private LedgerClient ledgerClient;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reconciliation_item");
        jdbcTemplate.update("delete from reconciliation_run");
        jdbcTemplate.update("delete from eod_run");
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void stubDay(String batchAmount, String journalAmount, String debit, String credit) {
        BigDecimal batch = amount(batchAmount);
        BigDecimal journal = amount(journalAmount);
        when(transactionClient.findBatches(BUSINESS_DATE)).thenReturn(List.of(
                new TransactionClient.PostingBatch("b-persist", "CARD_PURCHASE", "00000000006", batch, "USD",
                        "POSTED", null, NOON, List.of(new TransactionClient.PostingLeg("DR", batch),
                        new TransactionClient.PostingLeg("CR", batch)))));
        when(ledgerClient.findJournals(BUSINESS_DATE)).thenReturn(List.of(
                new LedgerClient.JournalEntry("JE-persist", "POSTING_BATCH", "b-persist", NOON, "POSTED",
                        List.of(new LedgerClient.JournalLine("1100", "DR", journal),
                                new LedgerClient.JournalLine("2100", "CR", journal)))));
        when(ledgerClient.trialBalance(PERIOD)).thenReturn(new LedgerClient.TrialBalance(
                PERIOD.toString(), amount(debit), amount(credit),
                amount(debit).compareTo(amount(credit)) == 0));
    }

    @Test
    void completedRunAndResolvedItemSurviveSeparateTransactions() {
        stubDay("25.00", "24.00", "24.00", "24.00");

        ReconciliationRunView view = reconciliations.run(BUSINESS_DATE);
        assertThat(view.run().getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(view.run().getMismatchCount()).isEqualTo(1);

        ReconciliationItem mismatch = view.items().stream()
                .filter(item -> item.getStatus() == ItemStatus.AMOUNT_MISMATCH)
                .findFirst()
                .orElseThrow();
        reconciliations.resolve(mismatch.getItemId(), "已确认总账同步延迟，手工补录");

        ReconciliationRunView reloaded = reconciliations.get(view.run().getRunId());
        assertThat(reloaded.run().getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(reloaded.items()).hasSize(2);
        assertThat(reloaded.items().stream()
                .filter(item -> item.getItemId().equals(mismatch.getItemId()))
                .findFirst()
                .orElseThrow()
                .isResolved()).isTrue();
    }

    @Test
    void failedRunIsKeptWhenDownstreamUnavailable() {
        when(transactionClient.findBatches(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        assertThatThrownBy(() -> reconciliations.run(BUSINESS_DATE))
                .isInstanceOf(RemoteServiceException.class);

        ReconciliationRun failed = reconciliations.list(0, 10).items().get(0);
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.getRunId()).isNotBlank();
        assertThat(failed.getErrorMessage()).contains("transaction-service is unavailable");
    }

    @Test
    void failedEodKeepsBothRecords() {
        stubDay("25.00", "25.00", "25.00", "25.00");

        EodRun completed = eods.start(BUSINESS_DATE);
        assertThat(completed.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(eods.get(completed.getRunId()).getReconciliationRunId())
                .isEqualTo(completed.getReconciliationRunId());

        when(transactionClient.findBatches(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));
        assertThatThrownBy(() -> eods.start(BUSINESS_DATE.plusDays(1)))
                .isInstanceOf(RemoteServiceException.class);

        assertThat(eods.list(0, 10).items())
                .extracting(EodRun::getStatus)
                .containsExactlyInAnyOrder(RunStatus.FAILED, RunStatus.COMPLETED);
        EodRun failedEod = eods.list(0, 10).items().stream()
                .filter(run -> run.getStatus() == RunStatus.FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(failedEod.getMessage()).contains("transaction-service is unavailable");
        assertThat(failedEod.getReconciliationRunId()).isNull();
        assertThat(reconciliations.list(0, 10).items())
                .extracting(ReconciliationRun::getStatus)
                .containsExactlyInAnyOrder(RunStatus.FAILED, RunStatus.COMPLETED);
    }
}
