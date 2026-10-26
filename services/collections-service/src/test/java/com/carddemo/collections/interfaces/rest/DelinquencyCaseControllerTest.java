package com.carddemo.collections.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 催收 REST 契约：H2 + Flyway + MockMvc（设计 §6.2）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DelinquencyCaseControllerTest {

    private static final String ACCOUNT_ID = "00000000006";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createCase(String accountId, int daysPastDue, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/delinquency-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","daysPastDue":%d,"outstandingAmount":%s}
                                """.formatted(accountId, daysPastDue, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("caseId").asText();
    }

    @Test
    void createsCaseWithBucketAndWalksTheLifecycle() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/delinquency-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","daysPastDue":45,"outstandingAmount":1234.56}
                                """.formatted(ACCOUNT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").isNotEmpty())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.daysPastDue").value(45))
                .andExpect(jsonPath("$.bucket").value("B2"))
                .andExpect(jsonPath("$.outstandingAmount").value(1234.56))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.promiseDate").value(nullValue()))
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        String caseId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("caseId").asText();

        mockMvc.perform(get("/api/v1/delinquency-cases/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.bucket").value("B2"));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/recalculate", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daysPastDue\":95}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysPastDue").value(95))
                .andExpect(jsonPath("$.bucket").value("B4"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        LocalDate promiseDate = LocalDate.now().plusDays(10);
        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/promise-to-pay", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + promiseDate + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PTP"))
                .andExpect(jsonPath("$.promiseDate").value(promiseDate.toString()));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/resolve", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.promiseDate").value(promiseDate.toString()));
    }

    @Test
    void listsCasesWithStatusAndBucketFilters() throws Exception {
        createCase(ACCOUNT_ID, 10, "100.00");
        createCase("00000000007", 45, "200.00");
        createCase("00000000008", 95, "300.00");

        mockMvc.perform(get("/api/v1/delinquency-cases").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3));

        mockMvc.perform(get("/api/v1/delinquency-cases").param("bucket", "B1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/delinquency-cases").param("status", "PTP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/delinquency-cases").param("status", "CLOSED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void unknownCaseReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/delinquency-cases/{caseId}", "missing-case"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELINQUENCY_CASE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/recalculate", "missing-case")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daysPastDue\":30}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELINQUENCY_CASE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/resolve", "missing-case"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELINQUENCY_CASE_NOT_FOUND"));
    }

    @Test
    void rejectsPromiseDateThatIsNotInTheFuture() throws Exception {
        String caseId = createCase(ACCOUNT_ID, 45, "100.00");

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/promise-to-pay", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROMISE_DATE"));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/promise-to-pay", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROMISE_DATE"));

        mockMvc.perform(get("/api/v1/delinquency-cases/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.promiseDate").value(nullValue()));
    }

    @Test
    void resolvedCaseCannotChangeAgain() throws Exception {
        String caseId = createCase(ACCOUNT_ID, 61, "100.00");

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/resolve", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/resolve", caseId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_RESOLVED"));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/recalculate", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daysPastDue\":120}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_RESOLVED"));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/promise-to-pay", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + LocalDate.now().plusDays(3) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_RESOLVED"));

        mockMvc.perform(get("/api/v1/delinquency-cases/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.daysPastDue").value(61));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        mockMvc.perform(post("/api/v1/delinquency-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/delinquency-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"00000000006","daysPastDue":0,"outstandingAmount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String caseId = createCase(ACCOUNT_ID, 45, "100.00");
        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/recalculate", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daysPastDue\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/delinquency-cases/{caseId}/promise-to-pay", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
