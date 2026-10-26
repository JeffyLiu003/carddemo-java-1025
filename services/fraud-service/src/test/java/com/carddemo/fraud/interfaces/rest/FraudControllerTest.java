package com.carddemo.fraud.interfaces.rest;

import com.carddemo.fraud.support.FraudTestClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 欺诈 REST 契约：种子规则命中、决策边界、警报生成与处置、分页与 404。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(FraudTestClockConfig.class)
class FraudControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetClock() {
        FraudTestClockConfig.CLOCK.moveTo(FraudTestClockConfig.BASE_INSTANT);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String payload(String accountId, String amount, String merchantName, String country)
            throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("transactionId", UUID.randomUUID().toString());
        node.put("accountId", accountId);
        node.put("amount", new BigDecimal(amount));
        node.put("merchantName", merchantName);
        node.put("country", country);
        return objectMapper.writeValueAsString(node);
    }

    private MvcResult evaluate(String accountId, String amount, String merchantName, String country)
            throws Exception {
        return mockMvc.perform(post("/api/v1/fraud-evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(accountId, amount, merchantName, country)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String evaluateAndGetAlertId(String accountId, String amount) throws Exception {
        MvcResult result = evaluate(accountId, amount, "Corner Grocery", "US");
        assertThat(body(result).get("decision").asText()).isEqualTo("REVIEW");
        return body(result).get("alertId").asText();
    }

    @Test
    void approvesLowRiskTransactionWithoutAlert() throws Exception {
        MvcResult result = evaluate("00000000001", "120.00", "Corner Grocery", "US");

        JsonNode body = body(result);
        assertThat(body.get("evaluationId").asText()).isNotBlank();
        assertThat(body.get("score").asInt()).isZero();
        assertThat(body.get("decision").asText()).isEqualTo("APPROVE");
        assertThat(body.get("matchedRules").isEmpty()).isTrue();
        assertThat(body.get("alertId").isNull()).isTrue();
        assertThat(body.get("createdAt").asText()).isEqualTo("2026-10-26T04:00:00");

        mockMvc.perform(get("/api/v1/fraud-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void reviewsAmountAboveThresholdAndOpensAlert() throws Exception {
        MvcResult result = evaluate("00000000001", "5000.01", "Corner Grocery", "US");

        JsonNode body = body(result);
        assertThat(body.get("score").asInt()).isEqualTo(60);
        assertThat(body.get("decision").asText()).isEqualTo("REVIEW");
        assertThat(body.get("matchedRules").get(0).asText()).isEqualTo("AMOUNT_GT");
        String alertId = body.get("alertId").asText();
        assertThat(alertId).isNotBlank();

        mockMvc.perform(get("/api/v1/fraud-evaluations/{evaluationId}", body.get("evaluationId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(60))
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.matchedRules[0]").value("AMOUNT_GT"))
                .andExpect(jsonPath("$.alertId").value(alertId));

        mockMvc.perform(get("/api/v1/fraud-alerts/{alertId}", alertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(alertId))
                .andExpect(jsonPath("$.evaluationId").value(body.get("evaluationId").asText()))
                .andExpect(jsonPath("$.accountId").value("00000000001"))
                .andExpect(jsonPath("$.score").value(60))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("AMOUNT_GT")))
                .andExpect(jsonPath("$.resolvedAt").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/fraud-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].alertId").value(alertId));
    }

    @Test
    void rejectsBlockedMerchantIgnoringCase() throws Exception {
        MvcResult result = evaluate("00000000001", "10.00", "blocked merchant store", "US");

        assertThat(body(result).get("score").asInt()).isEqualTo(100);
        assertThat(body(result).get("decision").asText()).isEqualTo("REJECT");
        assertThat(body(result).get("matchedRules").get(0).asText()).isEqualTo("MERCHANT_BLOCKED");

        mockMvc.perform(get("/api/v1/fraud-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void rejectsBlockedCountryIgnoringCase() throws Exception {
        MvcResult result = evaluate("00000000001", "10.00", "Corner Grocery", "ir");

        assertThat(body(result).get("score").asInt()).isEqualTo(80);
        assertThat(body(result).get("decision").asText()).isEqualTo("REJECT");
        assertThat(body(result).get("country").asText()).isEqualTo("IR");
    }

    @Test
    void approvesAmountExactlyAtThreshold() throws Exception {
        MvcResult result = evaluate("00000000001", "5000.00", "Corner Grocery", "US");

        assertThat(body(result).get("score").asInt()).isZero();
        assertThat(body(result).get("decision").asText()).isEqualTo("APPROVE");
    }

    @Test
    void confirmAndDismissResolveOpenAlerts() throws Exception {
        String confirmedAlertId = evaluateAndGetAlertId("00000000001", "6000.00");

        mockMvc.perform(post("/api/v1/fraud-alerts/{alertId}/confirm", confirmedAlertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.resolvedAt").value("2026-10-26T04:00:00"));

        mockMvc.perform(post("/api/v1/fraud-alerts/{alertId}/dismiss", confirmedAlertId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_ALREADY_RESOLVED"));

        String dismissedAlertId = evaluateAndGetAlertId("00000000001", "6000.00");

        mockMvc.perform(post("/api/v1/fraud-alerts/{alertId}/dismiss", dismissedAlertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.resolvedAt").value("2026-10-26T04:00:00"));

        mockMvc.perform(get("/api/v1/fraud-alerts").param("status", "DISMISSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].alertId").value(dismissedAlertId));
    }

    @Test
    void returns404ForUnknownEvaluationAndAlert() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-evaluations/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/fraud-alerts/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/fraud-alerts/{id}/confirm", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));
    }

    @Test
    void pagesAndFiltersAlertsNewestFirst() throws Exception {
        String first = evaluateAndGetAlertId("00000000001", "6000.00");
        FraudTestClockConfig.CLOCK.advance(Duration.ofMinutes(1));
        String second = evaluateAndGetAlertId("00000000002", "6000.00");
        FraudTestClockConfig.CLOCK.advance(Duration.ofMinutes(1));
        String third = evaluateAndGetAlertId("00000000003", "6000.00");

        MvcResult page0 = mockMvc.perform(get("/api/v1/fraud-alerts")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        assertThat(body(page0).get("items").get(0).get("alertId").asText()).isEqualTo(third);
        assertThat(body(page0).get("items").get(1).get("alertId").asText()).isEqualTo(second);

        MvcResult page1 = mockMvc.perform(get("/api/v1/fraud-alerts")
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
        assertThat(body(page1).get("items").get(0).get("alertId").asText()).isEqualTo(first);

        mockMvc.perform(get("/api/v1/fraud-alerts").param("accountId", "00000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].alertId").value(second));

        mockMvc.perform(get("/api/v1/fraud-alerts").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsInvalidRequestsAndQueries() throws Exception {
        mockMvc.perform(post("/api/v1/fraud-evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"00000000001","amount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/fraud-evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"TXN1","accountId":"00000000001","amount":-1.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/fraud-alerts").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/fraud-alerts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/fraud-alerts").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
