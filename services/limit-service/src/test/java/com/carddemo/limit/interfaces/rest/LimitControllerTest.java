package com.carddemo.limit.interfaces.rest;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 限额 REST 契约：创建、按范围查询、调整、占用、释放、可用性检查。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueScopeId() {
        return "acct-" + UUID.randomUUID();
    }

    private static String limitBody(String scopeType, String scopeId, String limitType,
                                    String currency, String amount) {
        return """
                {"scopeType":"%s","scopeId":"%s","limitType":"%s","currency":"%s","amount":%s,"active":true}
                """.formatted(scopeType, scopeId, limitType, currency, amount);
    }

    private String createLimit(String scopeId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("ACCOUNT", scopeId, "PURCHASE", "USD", amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return limitIdOf(result);
    }

    private String limitIdOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("limitId").asText();
    }

    @Test
    void createsLimitAndReadsItBack() throws Exception {
        String scopeId = uniqueScopeId();

        MvcResult created = mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("ACCOUNT", scopeId, "PURCHASE", "USD", "1000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopeType").value("ACCOUNT"))
                .andExpect(jsonPath("$.scopeId").value(scopeId))
                .andExpect(jsonPath("$.limitType").value("PURCHASE"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.usedAmount").value(0.00))
                .andExpect(jsonPath("$.remainingAmount").value(1000.00))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String limitId = limitIdOf(created);

        mockMvc.perform(get("/api/v1/limits/{limitId}", limitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitId").value(limitId))
                .andExpect(jsonPath("$.amount").value(1000.00));
    }

    @Test
    void listsLimitsByScopeWithPaging() throws Exception {
        String scopeId = uniqueScopeId();
        createLimit(scopeId, "1000.00");
        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("ACCOUNT", scopeId, "CASH", "USD", "500.00")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("CHANNEL", "WEB", "TRANSFER", "USD", "2000.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/limits")
                        .param("scopeType", "ACCOUNT")
                        .param("scopeId", scopeId)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].scopeId").value(scopeId));

        mockMvc.perform(get("/api/v1/limits").param("scopeType", "ACCOUNT").param("scopeId", scopeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].limitType").value("CASH"))
                .andExpect(jsonPath("$.items[1].limitType").value("PURCHASE"));
    }

    @Test
    void duplicateLimitForSameScopeConflicts() throws Exception {
        String scopeId = uniqueScopeId();
        createLimit(scopeId, "1000.00");

        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("ACCOUNT", scopeId, "PURCHASE", "USD", "2000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_ALREADY_DEFINED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adjustLimitRejectsAmountBelowUsed() throws Exception {
        String limitId = createLimit(uniqueScopeId(), "1000.00");
        mockMvc.perform(post("/api/v1/limits/{limitId}/usage", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":400.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedAmount").value(400.00))
                .andExpect(jsonPath("$.remainingAmount").value(600.00));

        mockMvc.perform(put("/api/v1/limits/{limitId}", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":399.99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_BELOW_USED"));

        mockMvc.perform(put("/api/v1/limits/{limitId}", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":800.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(800.00))
                .andExpect(jsonPath("$.remainingAmount").value(400.00));
    }

    @Test
    void reserveAndReleaseUsage() throws Exception {
        String limitId = createLimit(uniqueScopeId(), "300.00");

        mockMvc.perform(post("/api/v1/limits/{limitId}/usage", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedAmount").value(250.00))
                .andExpect(jsonPath("$.remainingAmount").value(50.00));

        mockMvc.perform(post("/api/v1/limits/{limitId}/usage", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.01}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));

        mockMvc.perform(post("/api/v1/limits/{limitId}/release", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedAmount").value(0.00))
                .andExpect(jsonPath("$.remainingAmount").value(300.00));

        mockMvc.perform(post("/api/v1/limits/{limitId}/release", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT_RELEASE"));
    }

    @Test
    void checkReturnsAllowedWhenWithinRemaining() throws Exception {
        String scopeId = uniqueScopeId();
        String limitId = createLimit(scopeId, "1000.00");
        mockMvc.perform(post("/api/v1/limits/{limitId}/usage", limitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/limits/check")
                        .param("scopeType", "ACCOUNT")
                        .param("scopeId", scopeId)
                        .param("limitType", "PURCHASE")
                        .param("currency", "USD")
                        .param("amount", "750.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.limitId").value(limitId))
                .andExpect(jsonPath("$.usedAmount").value(250.00))
                .andExpect(jsonPath("$.remainingAmount").value(750.00));

        mockMvc.perform(get("/api/v1/limits/check")
                        .param("scopeType", "ACCOUNT")
                        .param("scopeId", scopeId)
                        .param("limitType", "PURCHASE")
                        .param("currency", "USD")
                        .param("amount", "750.01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.remainingAmount").value(750.00));
    }

    @Test
    void checkWithoutMatchingLimitIsNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/limits/check")
                        .param("scopeType", "CUSTOMER")
                        .param("scopeId", "000000009")
                        .param("limitType", "DAILY")
                        .param("currency", "USD")
                        .param("amount", "10.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.limitId").isEmpty())
                .andExpect(jsonPath("$.usedAmount").value(0.00))
                .andExpect(jsonPath("$.remainingAmount").value(0.00));
    }

    @Test
    void unknownLimitReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/limits/{limitId}", "missing-limit"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LIMIT_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/limits/{limitId}", "missing-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LIMIT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidRequestWith400() throws Exception {
        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("ACCOUNT", "", "PURCHASE", "USD", "1000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("ACCOUNT", uniqueScopeId(), "PURCHASE", "USD", "0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("ACCOUNT", uniqueScopeId(), "PURCHASE", "US", "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitBody("BRANCH", uniqueScopeId(), "PURCHASE", "USD", "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/limits/check")
                        .param("scopeType", "ACCOUNT")
                        .param("scopeId", uniqueScopeId())
                        .param("limitType", "PURCHASE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
