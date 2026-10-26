package com.carddemo.reconciliation.interfaces.rest;

import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import com.carddemo.reconciliation.infrastructure.client.LedgerClient;
import com.carddemo.reconciliation.infrastructure.client.TransactionClient;
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
import java.time.LocalDateTime;
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
 * 对账与 EOD REST 契约：H2 + Flyway + MockMvc；transaction-service / ledger-service 用 @MockBean 替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReconciliationControllerTest {

    private static final String BUSINESS_DATE = "2026-10-26";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);
    private static final YearMonth PERIOD = YearMonth.of(2026, 10);
    private static final LocalDateTime NOON = DATE.atTime(12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionClient transactionClient;

    @MockBean
    private LedgerClient ledgerClient;

    private static TransactionClient.PostingBatch batch(String batchId, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new TransactionClient.PostingBatch(batchId, "CARD_PURCHASE", "00000000006", value, "USD",
                "POSTED", null, NOON, List.of(new TransactionClient.PostingLeg("DR", value),
                new TransactionClient.PostingLeg("CR", value)));
    }

    private static LedgerClient.JournalEntry journal(String sourceId, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new LedgerClient.JournalEntry("JE-" + sourceId, "POSTING_BATCH", sourceId, NOON, "POSTED",
                List.of(new LedgerClient.JournalLine("1100", "DR", value),
                        new LedgerClient.JournalLine("2100", "CR", value)));
    }

    private static LedgerClient.TrialBalance trialBalance(String debit, String credit) {
        BigDecimal totalDebit = new BigDecimal(debit);
        BigDecimal totalCredit = new BigDecimal(credit);
        return new LedgerClient.TrialBalance("2026-10", totalDebit, totalCredit,
                totalDebit.compareTo(totalCredit) == 0);
    }

    private void stubMatchedDay() {
        when(transactionClient.findBatches(DATE)).thenReturn(List.of(batch("b-1", "25.00"), batch("b-2", "25.00")));
        when(ledgerClient.findJournals(DATE)).thenReturn(List.of(journal("b-1", "25.00"), journal("b-2", "25.00")));
        when(ledgerClient.trialBalance(PERIOD)).thenReturn(trialBalance("50.00", "50.00"));
    }

    private void stubMissingJournalDay() {
        when(transactionClient.findBatches(DATE)).thenReturn(List.of(batch("b-1", "25.00"), batch("b-2", "40.00")));
        when(ledgerClient.findJournals(DATE)).thenReturn(List.of(journal("b-1", "25.00")));
        when(ledgerClient.trialBalance(PERIOD)).thenReturn(trialBalance("25.00", "25.00"));
    }

    private static String body(String businessDate) {
        return """
                {"businessDate":"%s"}
                """.formatted(businessDate);
    }

    private String createRun() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reconciliation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(BUSINESS_DATE)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("runId").asText();
    }

    @Test
    void createsRunWithItemsAndReadsDetailHistoryAndItems() throws Exception {
        stubMatchedDay();

        MvcResult created = mockMvc.perform(post("/api/v1/reconciliation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(BUSINESS_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE))
                .andExpect(jsonPath("$.period").value("2026-10"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.batchCount").value(2))
                .andExpect(jsonPath("$.batchAmount").value(50.00))
                .andExpect(jsonPath("$.journalCount").value(2))
                .andExpect(jsonPath("$.journalDebitTotal").value(50.00))
                .andExpect(jsonPath("$.matchCount").value(3))
                .andExpect(jsonPath("$.mismatchCount").value(0))
                .andExpect(jsonPath("$.missingJournalCount").value(0))
                .andExpect(jsonPath("$.missingBatchCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].checkType").value("POSTING_VS_JOURNAL"))
                .andExpect(jsonPath("$.items[0].referenceId").value("b-1"))
                .andExpect(jsonPath("$.items[0].status").value("MATCH"))
                .andExpect(jsonPath("$.items[0].difference").value(0.00))
                .andExpect(jsonPath("$.items[2].checkType").value("TRIAL_BALANCE"))
                .andExpect(jsonPath("$.items[2].status").value("MATCH"))
                .andReturn();
        String runId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("runId").asText();

        mockMvc.perform(get("/api/v1/reconciliation-runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.items.length()").value(3));

        mockMvc.perform(get("/api/v1/reconciliation-runs").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].runId").value(runId));

        mockMvc.perform(get("/api/v1/reconciliation-runs/{runId}/items", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void resolveItemStoresNoteAndSurvivesReload() throws Exception {
        stubMissingJournalDay();
        String runId = createRun();

        MvcResult items = mockMvc.perform(get("/api/v1/reconciliation-runs/{runId}/items", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[1].status").value("MISSING_JOURNAL"))
                .andExpect(jsonPath("$.items[1].difference").value(40.00))
                .andReturn();
        String itemId = objectMapper.readTree(items.getResponse().getContentAsString())
                .get("items").get(1).get("itemId").asText();

        mockMvc.perform(post("/api/v1/reconciliation-items/{itemId}/resolve", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"已确认总账同步延迟，手工补录\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(itemId))
                .andExpect(jsonPath("$.status").value("MISSING_JOURNAL"))
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.resolutionNote").value("已确认总账同步延迟，手工补录"));

        mockMvc.perform(get("/api/v1/reconciliation-runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].resolved").value(true))
                .andExpect(jsonPath("$.items[1].resolutionNote").value("已确认总账同步延迟，手工补录"));
    }

    @Test
    void unknownRunAndItemReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation-runs/{runId}", "missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/v1/reconciliation-runs/{runId}/items", "missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_RUN_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/reconciliation-items/{itemId}/resolve", "missing-item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"n/a\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_ITEM_NOT_FOUND"));
    }

    @Test
    void rejectsRequestWithoutBusinessDate() throws Exception {
        mockMvc.perform(post("/api/v1/reconciliation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/eod-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void downstreamUnavailableReturns503() throws Exception {
        when(transactionClient.findBatches(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(post("/api/v1/reconciliation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(BUSINESS_DATE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("transaction-service is unavailable"));

        mockMvc.perform(get("/api/v1/reconciliation-runs").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].errorMessage").value("transaction-service is unavailable"));
    }

    @Test
    void eodRunCompletesAndIsReadable() throws Exception {
        stubMatchedDay();

        MvcResult created = mockMvc.perform(post("/api/v1/eod-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(BUSINESS_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reconciliationRunId").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Reconciliation matched"))
                .andReturn();
        String eodRunId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("runId").asText();

        mockMvc.perform(get("/api/v1/eod-runs/{runId}", eodRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(eodRunId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/eod-runs").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/eod-runs/{runId}", "missing-eod"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD_RUN_NOT_FOUND"));
    }

    @Test
    void eodFailureReturns503AndKeepsFailedHistory() throws Exception {
        when(transactionClient.findBatches(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(post("/api/v1/eod-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(BUSINESS_DATE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        MvcResult history = mockMvc.perform(get("/api/v1/eod-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andReturn();

        JsonNode failed = objectMapper.readTree(history.getResponse().getContentAsString()).get("items").get(0);
        assertThat(failed.get("message").asText()).contains("transaction-service is unavailable");
        assertThat(failed.get("reconciliationRunId").isNull()).isTrue();
    }
}
