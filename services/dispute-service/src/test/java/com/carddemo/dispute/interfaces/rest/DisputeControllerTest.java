package com.carddemo.dispute.interfaces.rest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 争议案件端到端用例：H2 + Flyway + MockMvc（设计 14 §5.2）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DisputeControllerTest {

    private static final String ACCOUNT_ID = "00000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void opensDisputeCase() throws Exception {
        mockMvc.perform(post("/api/v1/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("txn-0001", ACCOUNT_ID, "250.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").isNotEmpty())
                .andExpect(jsonPath("$.transactionId").value("txn-0001"))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.customerId").value("00000000001"))
                .andExpect(jsonPath("$.reasonCode").value("FRAUD"))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.provisionalCreditAmount").value(0.00))
                .andExpect(jsonPath("$.openedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").isEmpty());
    }

    @Test
    void opensDisputeWithoutTransactionIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"00000000001","customerId":"00000000001",
                                 "reasonCode":"FRAUD","amount":250.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value("transactionId: must not be blank"));
    }

    @Test
    void opensDisputeWithNonPositiveAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("txn-0002", ACCOUNT_ID, "0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void walksLegalChainToWonThenClosed() throws Exception {
        String caseId = openCase("txn-0010");

        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(post("/api/v1/disputes/{caseId}/provisional-credit", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVISIONAL_CREDIT"))
                .andExpect(jsonPath("$.provisionalCreditAmount").value(100.00));

        mockMvc.perform(post("/api/v1/disputes/{caseId}/chargeback", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHARGEBACK"));

        mockMvc.perform(post("/api/v1/disputes/{caseId}/resolve", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"won\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WON"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/disputes/{caseId}/resolve", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"won\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void resolvesToLostWhenWonIsFalse() throws Exception {
        String caseId = openCase("txn-0011");
        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", caseId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/disputes/{caseId}/provisional-credit", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/disputes/{caseId}/chargeback", caseId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/disputes/{caseId}/resolve", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"won\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
    }

    @Test
    void provisionalCreditBeforeReviewReturns409() throws Exception {
        String caseId = openCase("txn-0012");

        mockMvc.perform(post("/api/v1/disputes/{caseId}/provisional-credit", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_UNDER_REVIEW"));
    }

    @Test
    void provisionalCreditAboveDisputedAmountReturns409() throws Exception {
        String caseId = openCase("txn-0013");
        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", caseId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/disputes/{caseId}/provisional-credit", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.01}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVISIONAL_CREDIT_EXCEEDS_DISPUTE_AMOUNT"));

        mockMvc.perform(get("/api/v1/disputes/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.provisionalCreditAmount").value(0.00));
    }

    @Test
    void provisionalCreditWithoutAmountReturns400() throws Exception {
        String caseId = openCase("txn-0014");
        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", caseId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/disputes/{caseId}/provisional-credit", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void illegalTransitionsReturn409() throws Exception {
        String caseId = openCase("txn-0015");

        mockMvc.perform(post("/api/v1/disputes/{caseId}/chargeback", caseId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_PROVISIONAL_CREDIT"));

        mockMvc.perform(post("/api/v1/disputes/{caseId}/resolve", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"won\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_CHARGEBACK"));

        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", caseId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", caseId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_OPEN"));
    }

    @Test
    void resolveWithoutWonReturns400() throws Exception {
        String caseId = openCase("txn-0016");

        mockMvc.perform(post("/api/v1/disputes/{caseId}/resolve", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsDetailAnd404ForUnknownCase() throws Exception {
        String caseId = openCase("txn-0017");

        mockMvc.perform(get("/api/v1/disputes/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/disputes/{caseId}", "unknown-case"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", "unknown-case"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));
    }

    @Test
    void listsCasesWithPaginationAndFilters() throws Exception {
        openCase("txn-0020");
        String reviewedCaseId = openCase("txn-0021");
        mockMvc.perform(post("/api/v1/disputes/{caseId}/review", reviewedCaseId))
                .andExpect(status().isOk());
        openCase("txn-0022", "00000000002");

        mockMvc.perform(get("/api/v1/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items.length()").value(3));

        mockMvc.perform(get("/api/v1/disputes").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/disputes").param("status", "UNDER_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].caseId").value(reviewedCaseId));

        mockMvc.perform(get("/api/v1/disputes").param("accountId", "00000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/disputes").param("accountId", "00000000009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void invalidStatusFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/disputes").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DISPUTE_STATUS"));
    }

    private String openCase(String transactionId) throws Exception {
        return openCase(transactionId, ACCOUNT_ID);
    }

    private String openCase(String transactionId, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest(transactionId, accountId, "250.00")))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("caseId").asText();
    }

    private static String openRequest(String transactionId, String accountId, String amount) {
        return """
                {"transactionId":"%s","accountId":"%s","customerId":"00000000001",
                 "reasonCode":"FRAUD","amount":%s}
                """.formatted(transactionId, accountId, amount);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
