package com.carddemo.aml.interfaces.rest;

import com.carddemo.aml.support.AmlTestClockConfig;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AML REST 契约：完全/包含/无匹配、姓名归一化、警报处置、分页与 404。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(AmlTestClockConfig.class)
class AmlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetClock() {
        AmlTestClockConfig.CLOCK.moveTo(AmlTestClockConfig.BASE_INSTANT);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String payload(String name, String country, String entityType, String customerId)
            throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("country", country);
        node.put("entityType", entityType);
        node.put("customerId", customerId);
        return objectMapper.writeValueAsString(node);
    }

    private MvcResult screen(String name, String country, String entityType, String customerId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(name, country, entityType, customerId)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String screenAndGetAlertId(String name) throws Exception {
        MvcResult result = screen(name, "RU", "INDIVIDUAL", "000000001");
        String alertId = body(result).get("alertId").asText();
        assertThat(alertId).isNotBlank();
        return alertId;
    }

    @Test
    void screensExactMatchAfterNormalizationAndOpensAlert() throws Exception {
        MvcResult result = screen("  Ivan   PETROV. ", "ru", "individual", "000000001");

        JsonNode body = body(result);
        assertThat(body.get("result").asText()).isEqualTo("BLOCKED");
        assertThat(body.get("matchedEntryId").asText()).isEqualTo("sanctions-ivan-petrov");
        assertThat(body.get("score").asInt()).isEqualTo(100);
        assertThat(body.get("country").asText()).isEqualTo("RU");
        assertThat(body.get("entityType").asText()).isEqualTo("INDIVIDUAL");
        assertThat(body.get("name").asText()).isEqualTo("Ivan   PETROV.");
        assertThat(body.get("createdAt").asText()).isEqualTo("2026-10-26T04:00:00");
        String screeningId = body.get("screeningId").asText();
        String alertId = body.get("alertId").asText();
        assertThat(alertId).isNotBlank();

        mockMvc.perform(get("/api/v1/screenings/{screeningId}", screeningId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("BLOCKED"))
                .andExpect(jsonPath("$.matchedEntryId").value("sanctions-ivan-petrov"))
                .andExpect(jsonPath("$.alertId").value(alertId));

        mockMvc.perform(get("/api/v1/aml-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items[0].alertId").value(alertId))
                .andExpect(jsonPath("$.items[0].screeningId").value(screeningId))
                .andExpect(jsonPath("$.items[0].customerId").value("000000001"))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].details")
                        .value(org.hamcrest.Matchers.containsString("sanctions-ivan-petrov")))
                .andExpect(jsonPath("$.items[0].resolvedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void screensPartialMatchAsPotentialMatch() throws Exception {
        MvcResult result = screen("ACME FRAUD LTD HOLDINGS", "KP", "ENTITY", null);

        JsonNode body = body(result);
        assertThat(body.get("result").asText()).isEqualTo("POTENTIAL_MATCH");
        assertThat(body.get("matchedEntryId").asText()).isEqualTo("sanctions-acme-fraud-ltd");
        assertThat(body.get("score").asInt()).isEqualTo(70);
        assertThat(body.get("alertId").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/aml-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void screensWithoutMatchAsClearAndCreatesNoAlert() throws Exception {
        MvcResult result = screen("John Smith", "US", "INDIVIDUAL", "000000002");

        JsonNode body = body(result);
        assertThat(body.get("result").asText()).isEqualTo("CLEAR");
        assertThat(body.get("score").asInt()).isZero();
        assertThat(body.get("matchedEntryId").isNull()).isTrue();
        assertThat(body.get("alertId").isNull()).isTrue();

        mockMvc.perform(get("/api/v1/aml-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void clearAndEscalateAreTerminal() throws Exception {
        String clearedAlertId = screenAndGetAlertId("Ivan Petrov");

        mockMvc.perform(post("/api/v1/aml-alerts/{alertId}/clear", clearedAlertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.resolvedAt").value("2026-10-26T04:00:00"));

        mockMvc.perform(post("/api/v1/aml-alerts/{alertId}/escalate", clearedAlertId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_ALREADY_RESOLVED"));

        String escalatedAlertId = screenAndGetAlertId("Blocked Trading");

        mockMvc.perform(post("/api/v1/aml-alerts/{alertId}/escalate", escalatedAlertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"))
                .andExpect(jsonPath("$.resolvedAt").value("2026-10-26T04:00:00"));

        mockMvc.perform(get("/api/v1/aml-alerts").param("status", "ESCALATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].alertId").value(escalatedAlertId));
    }

    @Test
    void returns404ForUnknownScreeningAndAlert() throws Exception {
        mockMvc.perform(get("/api/v1/screenings/{screeningId}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCREENING_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/aml-alerts/{alertId}/clear", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/aml-alerts/{alertId}/escalate", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));
    }

    @Test
    void pagesAlertsNewestFirst() throws Exception {
        String first = screenAndGetAlertId("Ivan Petrov");
        AmlTestClockConfig.CLOCK.advance(Duration.ofMinutes(1));
        String second = screenAndGetAlertId("Ivan Petrov");
        AmlTestClockConfig.CLOCK.advance(Duration.ofMinutes(1));
        String third = screenAndGetAlertId("Ivan Petrov");

        MvcResult page0 = mockMvc.perform(get("/api/v1/aml-alerts")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        assertThat(body(page0).get("items").get(0).get("alertId").asText()).isEqualTo(third);
        assertThat(body(page0).get("items").get(1).get("alertId").asText()).isEqualTo(second);

        MvcResult page1 = mockMvc.perform(get("/api/v1/aml-alerts")
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
        assertThat(body(page1).get("items").get(0).get("alertId").asText()).isEqualTo(first);

        mockMvc.perform(get("/api/v1/aml-alerts").param("status", "CLEARED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsInvalidScreeningRequestsAndQueries() throws Exception {
        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"US\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"... ---\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"John Smith\",\"country\":\"USA1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/aml-alerts").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/aml-alerts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
