package com.carddemo.reporting.interfaces.rest;

import com.carddemo.reporting.domain.exception.RemoteServiceException;
import com.carddemo.reporting.infrastructure.client.LedgerTrialBalanceClient;
import com.carddemo.reporting.infrastructure.client.TransactionReportClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 报表 REST 契约：H2 + Flyway + MockMvc；transaction-service / ledger-service 用 @MockBean 替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerTest {

    private static final String BUSINESS_DATE = "2026-10-26";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);
    private static final YearMonth PERIOD = YearMonth.of(2026, 10);
    private static final String CARD_ONE = "4000000000000001";
    private static final String CARD_TWO = "4000000000000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionReportClient transactionClient;

    @MockBean
    private LedgerTrialBalanceClient ledgerClient;

    private void stubTransactions() {
        when(transactionClient.transactionsOn(DATE)).thenReturn(new TransactionReportClient.TransactionReport(
                List.of(new TransactionReportClient.TransactionLine(CARD_ONE, new BigDecimal("25.00")),
                        new TransactionReportClient.TransactionLine(CARD_ONE, new BigDecimal("15.00")),
                        new TransactionReportClient.TransactionLine(CARD_TWO, new BigDecimal("30.00"))),
                3));
    }

    private void stubTrialBalance() {
        when(ledgerClient.trialBalance(PERIOD)).thenReturn(new LedgerTrialBalanceClient.TrialBalance(
                "2026-10", new BigDecimal("50.00"), new BigDecimal("50.00"), true,
                List.of(new LedgerTrialBalanceClient.TrialBalanceItem("1100", new BigDecimal("50.00"),
                                new BigDecimal("0.00"), new BigDecimal("50.00")),
                        new LedgerTrialBalanceClient.TrialBalanceItem("2100", new BigDecimal("0.00"),
                                new BigDecimal("50.00"), new BigDecimal("-50.00")))));
    }

    private static String body(String reportCode) {
        return """
                {"reportCode":"%s","businessDate":"%s"}
                """.formatted(reportCode, BUSINESS_DATE);
    }

    private String generate(String reportCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/report-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(reportCode)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("runId").asText();
    }

    @Test
    void listsReportDefinitionsFromSeed() throws Exception {
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].reportCode").value("DAILY_TRANSACTION_SUMMARY"))
                .andExpect(jsonPath("$.items[0].sourceService").value("transaction"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[1].reportCode").value("TRIAL_BALANCE"))
                .andExpect(jsonPath("$.items[1].sourceService").value("ledger"));
    }

    @Test
    void generatesDailyTransactionSummaryAndReadsDetailAndHistory() throws Exception {
        stubTransactions();

        MvcResult created = mockMvc.perform(post("/api/v1/report-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("DAILY_TRANSACTION_SUMMARY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.reportCode").value("DAILY_TRANSACTION_SUMMARY"))
                .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rowCount").value(3))
                .andExpect(jsonPath("$.message").value("Generated 3 rows for 2026-10-26"))
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].lineNo").value(1))
                .andExpect(jsonPath("$.rows[0].dimension").value(CARD_ONE))
                .andExpect(jsonPath("$.rows[0].amount").value(40.00))
                .andExpect(jsonPath("$.rows[0].detail").value("2 transactions"))
                .andExpect(jsonPath("$.rows[2].dimension").value("TOTAL"))
                .andExpect(jsonPath("$.rows[2].amount").value(70.00))
                .andReturn();
        String runId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("runId").asText();

        mockMvc.perform(get("/api/v1/report-runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[1].dimension").value(CARD_TWO));

        mockMvc.perform(get("/api/v1/report-runs")
                        .param("reportCode", "DAILY_TRANSACTION_SUMMARY")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].runId").value(runId))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"));
    }

    @Test
    void generatesTrialBalanceWithPerAccountRows() throws Exception {
        stubTrialBalance();

        mockMvc.perform(post("/api/v1/report-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("TRIAL_BALANCE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rowCount").value(2))
                .andExpect(jsonPath("$.rows[0].dimension").value("1100"))
                .andExpect(jsonPath("$.rows[0].amount").value(50.00))
                .andExpect(jsonPath("$.rows[0].detail").value("debit 50.00, credit 0.00"))
                .andExpect(jsonPath("$.rows[1].dimension").value("2100"))
                .andExpect(jsonPath("$.rows[1].amount").value(-50.00));
    }

    @Test
    void unknownReportAndRunReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/report-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("NO_SUCH_REPORT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_DEFINITION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/v1/report-runs/{runId}", "missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_RUN_NOT_FOUND"));
    }

    @Test
    void rejectsRequestWithoutReportCodeOrBusinessDate() throws Exception {
        mockMvc.perform(post("/api/v1/report-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void downstreamUnavailableReturns503AndKeepsFailedHistory() throws Exception {
        when(transactionClient.transactionsOn(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(post("/api/v1/report-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("DAILY_TRANSACTION_SUMMARY")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("transaction-service is unavailable"));

        MvcResult history = mockMvc.perform(get("/api/v1/report-runs").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andReturn();

        JsonNode failed = objectMapper.readTree(history.getResponse().getContentAsString()).get("items").get(0);
        assertThat(failed.get("message").asText()).contains("transaction-service is unavailable");
        assertThat(failed.get("rowCount").asInt()).isZero();

        mockMvc.perform(get("/api/v1/report-runs/{runId}", failed.get("runId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }
}
