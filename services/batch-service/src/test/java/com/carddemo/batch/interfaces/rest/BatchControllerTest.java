package com.carddemo.batch.interfaces.rest;

import com.carddemo.batch.domain.exception.RemoteServiceException;
import com.carddemo.batch.infrastructure.client.ReconciliationJobClient;
import com.carddemo.batch.infrastructure.client.TransactionJobClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批处理 REST 契约：H2 + Flyway + MockMvc；transaction-service / reconciliation-service 用 @MockBean 替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BatchControllerTest {

    private static final String BUSINESS_DATE = "2026-10-26";
    private static final String INTEREST_SUMMARY =
            "Interest run for 2026-10-26 posted for 2 accounts, interest booked 2, total 45.67";
    private static final String RECONCILIATION_SUMMARY =
            "Reconciliation run run-1 finished with status COMPLETED, mismatches 0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionJobClient transactionJobClient;

    @MockBean
    private ReconciliationJobClient reconciliationJobClient;

    private static String body(String jobCode, String businessDate) {
        return """
                {"jobCode":"%s","businessDate":"%s"}
                """.formatted(jobCode, businessDate);
    }

    private void stubSuccess() {
        when(transactionJobClient.runInterest(any())).thenReturn(INTEREST_SUMMARY);
        when(reconciliationJobClient.runReconciliation(any())).thenReturn(RECONCILIATION_SUMMARY);
    }

    private String runJob(String jobCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/job-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(jobCode, BUSINESS_DATE)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("runId").asText();
    }

    @Test
    void listsSeededJobDefinitions() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].jobCode").value("INTEREST_RUN"))
                .andExpect(jsonPath("$.items[0].targetService").value("transaction"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[1].jobCode").value("RECONCILIATION"))
                .andExpect(jsonPath("$.items[1].targetService").value("reconciliation"));
    }

    @Test
    void interestJobCompletesAndIsReadable() throws Exception {
        stubSuccess();

        MvcResult created = mockMvc.perform(post("/api/v1/job-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("INTEREST_RUN", BUSINESS_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.jobCode").value("INTEREST_RUN"))
                .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.message").value(INTEREST_SUMMARY))
                .andReturn();
        String runId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("runId").asText();

        mockMvc.perform(get("/api/v1/job-runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value(INTEREST_SUMMARY));
    }

    @Test
    void reconciliationJobCompletesWithDownstreamSummary() throws Exception {
        stubSuccess();

        mockMvc.perform(post("/api/v1/job-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("RECONCILIATION", BUSINESS_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobCode").value("RECONCILIATION"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value(RECONCILIATION_SUMMARY));
    }

    @Test
    void historyFiltersAndPaginates() throws Exception {
        stubSuccess();
        runJob("INTEREST_RUN");
        runJob("INTEREST_RUN");
        runJob("RECONCILIATION");

        mockMvc.perform(get("/api/v1/job-runs").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(3));

        mockMvc.perform(get("/api/v1/job-runs")
                        .param("jobCode", "INTEREST_RUN")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].jobCode").value("INTEREST_RUN"))
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/job-runs").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/job-runs").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void unknownJobAndUnknownRunReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/job-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("MISSING_JOB", BUSINESS_DATE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_DEFINITION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/v1/job-runs/{runId}", "missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_RUN_NOT_FOUND"));
    }

    @Test
    void downstreamUnavailableReturns503AndKeepsFailedRun() throws Exception {
        when(transactionJobClient.runInterest(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(post("/api/v1/job-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("INTEREST_RUN", BUSINESS_DATE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("transaction-service is unavailable"));

        MvcResult history = mockMvc.perform(get("/api/v1/job-runs")
                        .param("status", "FAILED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        JsonNode failed = objectMapper.readTree(history.getResponse().getContentAsString()).get("items").get(0);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("jobCode").asText()).isEqualTo("INTEREST_RUN");
        assertThat(failed.get("message").asText()).isEqualTo("transaction-service is unavailable");
        assertThat(failed.get("completedAt").isNull()).isFalse();
    }

    @Test
    void rejectsInvalidExecutionRequests() throws Exception {
        mockMvc.perform(post("/api/v1/job-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/job-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobCode":"INTEREST_RUN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/job-runs").param("jobCode", "INTEREST_RUN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }
}
