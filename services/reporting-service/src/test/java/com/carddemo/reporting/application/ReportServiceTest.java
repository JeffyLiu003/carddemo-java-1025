package com.carddemo.reporting.application;

import com.carddemo.reporting.domain.exception.BusinessRuleException;
import com.carddemo.reporting.domain.exception.NotFoundException;
import com.carddemo.reporting.domain.exception.RemoteServiceException;
import com.carddemo.reporting.domain.model.ReportRow;
import com.carddemo.reporting.domain.model.ReportRun;
import com.carddemo.reporting.domain.model.RunStatus;
import com.carddemo.reporting.domain.repository.PageResult;
import com.carddemo.reporting.infrastructure.client.LedgerTrialBalanceClient;
import com.carddemo.reporting.infrastructure.client.TransactionReportClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 报表用例：真实 H2 + Flyway，transaction-service / ledger-service 用 @MockBean 替换；
 * 事务回滚保证用例间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportServiceTest {

    private static final String CARD_ONE = "4000000000000001";
    private static final String CARD_TWO = "4000000000000002";
    private static final String DAILY = "DAILY_TRANSACTION_SUMMARY";
    private static final String TRIAL = "TRIAL_BALANCE";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);

    @Autowired
    private ReportService reports;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private TransactionReportClient transactionClient;

    @MockBean
    private LedgerTrialBalanceClient ledgerClient;

    private static TransactionReportClient.TransactionLine line(String cardNumber, String amount) {
        return new TransactionReportClient.TransactionLine(cardNumber, new BigDecimal(amount));
    }

    private static LedgerTrialBalanceClient.TrialBalanceItem account(String code, String debit, String credit,
                                                                     String net) {
        return new LedgerTrialBalanceClient.TrialBalanceItem(code, new BigDecimal(debit),
                new BigDecimal(credit), new BigDecimal(net));
    }

    private void stubTransactions(TransactionReportClient.TransactionLine... lines) {
        when(transactionClient.transactionsOn(DATE))
                .thenReturn(new TransactionReportClient.TransactionReport(List.of(lines), lines.length));
    }

    private void stubTrialBalance(LedgerTrialBalanceClient.TrialBalanceItem... items) {
        when(ledgerClient.trialBalance(YearMonth.of(2026, 10)))
                .thenReturn(new LedgerTrialBalanceClient.TrialBalance("2026-10",
                        new BigDecimal("150.00"), new BigDecimal("150.00"), true, List.of(items)));
    }

    @Test
    void generatesDailyTransactionSummaryPerCardWithTotalRow() {
        stubTransactions(line(CARD_ONE, "25.00"), line(CARD_ONE, "15.00"), line(CARD_TWO, "30.00"));

        ReportRunView view = reports.generate(DAILY, DATE);

        assertThat(view.run().getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(view.run().getBusinessDate()).isEqualTo(DATE);
        assertThat(view.run().getRowCount()).isEqualTo(3);
        assertThat(view.run().getCompletedAt()).isNotNull();
        assertThat(view.run().getMessage()).contains("3 rows");

        List<ReportRow> rows = view.rows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getLineNo()).isEqualTo(1);
        assertThat(rows.get(0).getDimension()).isEqualTo(CARD_ONE);
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("40.00");
        assertThat(rows.get(0).getDetail()).isEqualTo("2 transactions");
        assertThat(rows.get(1).getDimension()).isEqualTo(CARD_TWO);
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("30.00");
        assertThat(rows.get(2).getLineNo()).isEqualTo(3);
        assertThat(rows.get(2).getDimension()).isEqualTo("TOTAL");
        assertThat(rows.get(2).getAmount()).isEqualByComparingTo("70.00");
        assertThat(rows.get(2).getDetail()).contains("3 transactions").contains("2 cards");

        ReportRunView reloaded = reports.get(view.run().getRunId());
        assertThat(reloaded.rows()).extracting(ReportRow::getRowId)
                .containsExactlyElementsOf(rows.stream().map(ReportRow::getRowId).toList());
    }

    @Test
    void generatesEmptyDayWithTotalRowOnly() {
        stubTransactions();

        ReportRunView view = reports.generate(DAILY, DATE);

        assertThat(view.run().getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(view.run().getRowCount()).isEqualTo(1);
        assertThat(view.rows()).hasSize(1);
        assertThat(view.rows().get(0).getDimension()).isEqualTo("TOTAL");
        assertThat(view.rows().get(0).getAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void generatesTrialBalanceRowPerAccountWithDebitCreditDetail() {
        stubTrialBalance(account("1100", "150.00", "0.00", "150.00"),
                account("2100", "0.00", "150.00", "-150.00"));

        ReportRunView view = reports.generate(TRIAL, DATE);

        assertThat(view.run().getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(view.run().getRowCount()).isEqualTo(2);
        List<ReportRow> rows = view.rows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getDimension()).isEqualTo("1100");
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("150.00");
        assertThat(rows.get(0).getDetail()).isEqualTo("debit 150.00, credit 0.00");
        assertThat(rows.get(1).getDimension()).isEqualTo("2100");
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("-150.00");
        assertThat(rows.get(1).getDetail()).isEqualTo("debit 0.00, credit 150.00");
    }

    @Test
    void rejectsUnknownReportCodeWithoutCreatingRun() {
        assertThatThrownBy(() -> reports.generate("NO_SUCH_REPORT", DATE))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("REPORT_DEFINITION_NOT_FOUND");

        assertThat(reports.listRuns(null, 0, 10).total()).isZero();
    }

    @Test
    void rejectsInactiveReport() {
        definition("INACTIVE_DAILY", "inactive daily report", "transaction", false);

        assertThatThrownBy(() -> reports.generate("INACTIVE_DAILY", DATE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("REPORT_INACTIVE");
        assertThat(reports.listRuns(null, 0, 10).total()).isZero();
    }

    @Test
    void rejectsUnsupportedSourceService() {
        definition("MYSTERY_REPORT", "report without a known source", "mystery", true);

        assertThatThrownBy(() -> reports.generate("MYSTERY_REPORT", DATE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("REPORT_SOURCE_UNSUPPORTED");
    }

    @Test
    void keepsFailedRunWhenDownstreamIsUnavailable() {
        when(transactionClient.transactionsOn(DATE))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        assertThatThrownBy(() -> reports.generate(DAILY, DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("unavailable");

        PageResult<ReportRun> history = reports.listRuns(DAILY, 0, 10);
        assertThat(history.total()).isEqualTo(1);
        ReportRun failed = history.items().get(0);
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.getMessage()).contains("transaction-service is unavailable");
        assertThat(failed.getRowCount()).isZero();
        assertThat(reports.get(failed.getRunId()).rows()).isEmpty();
    }

    @Test
    void listsRunsByReportCodeWithPagination() {
        when(transactionClient.transactionsOn(any())).thenReturn(
                new TransactionReportClient.TransactionReport(List.of(line(CARD_ONE, "10.00")), 1));
        when(ledgerClient.trialBalance(any())).thenReturn(new LedgerTrialBalanceClient.TrialBalance("2026-10",
                new BigDecimal("10.00"), new BigDecimal("10.00"), true,
                List.of(account("1100", "10.00", "0.00", "10.00"))));

        ReportRun first = reports.generate(DAILY, LocalDate.of(2026, 10, 25)).run();
        ReportRun second = reports.generate(DAILY, DATE).run();
        reports.generate(TRIAL, DATE);

        PageResult<ReportRun> page = reports.listRuns(DAILY, 0, 1);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getBusinessDate()).isEqualTo(DATE);
        assertThat(page.items().get(0).getRunId()).isIn(first.getRunId(), second.getRunId());
        assertThat(reports.listRuns(null, 0, 10).total()).isEqualTo(3);
        assertThat(reports.listRuns(DAILY, 1, 1).items()).hasSize(1);
    }

    @Test
    void rejectsUnknownRun() {
        assertThatThrownBy(() -> reports.get("missing-run"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("REPORT_RUN_NOT_FOUND");
    }

    private void definition(String reportCode, String name, String sourceService, boolean active) {
        entityManager.createNativeQuery("""
                        INSERT INTO report_definition (report_code, name, description, source_service, active, version)
                        VALUES (:code, :name, :name, :source, :active, 0)
                        """)
                .setParameter("code", reportCode)
                .setParameter("name", name)
                .setParameter("source", sourceService)
                .setParameter("active", active)
                .executeUpdate();
    }
}
