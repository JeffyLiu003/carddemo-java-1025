package com.carddemo.account.interfaces.rest;

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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private BigDecimal balanceOf(String accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("currentBalance").decimalValue();
    }

    @Test
    void returnsSeededAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/00000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("00000000001"))
                .andExpect(jsonPath("$.productCode").value("CLASSIC"))
                .andExpect(jsonPath("$.activeStatus").value("Y"));
    }

    @Test
    void listsSeededAccountProducts() throws Exception {
        mockMvc.perform(get("/api/v1/account-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].productCode").value("CLASSIC"))
                .andExpect(jsonPath("$.items[2].productCode").value("PLATINUM"));
    }

    @Test
    void returnsProductDetailsAnd404ForUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/account-products/GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxCreditLimit").value(10000.00));

        mockMvc.perform(get("/api/v1/account-products/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void returns404ForUnknownAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void postingPositiveAmountUpdatesBalanceAndCycleCredit() throws Exception {
        BigDecimal before = balanceOf("00000000003");

        mockMvc.perform(post("/api/v1/accounts/00000000003/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCycleCredit").value(10.00));

        assertThat(balanceOf("00000000003")).isEqualByComparingTo(before.add(new BigDecimal("10.00")));
    }

    @Test
    void billPaymentRejectsAccountWithZeroBalance() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/00000000005/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_PAY"));
    }

    @Test
    void updateRejectsInvalidStatus() throws Exception {
        String body = """
                {
                  "activeStatus": "X",
                  "currentBalance": 100.00,
                  "creditLimit": 2000.00,
                  "cashCreditLimit": 1000.00,
                  "openDate": "2020-01-01",
                  "expirationDate": "2028-01-01",
                  "reissueDate": "2028-01-01",
                  "currentCycleCredit": 0.00,
                  "currentCycleDebit": 0.00
                }
                """;

        mockMvc.perform(put("/api/v1/accounts/00000000004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void holdLifecycleUpdatesAvailableCredit() throws Exception {
        String reference = "test-hold-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/v1/accounts/00000000006/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00,"referenceType":"MANUAL","referenceId":"%s"}
                                """.formatted(reference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String holdId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("holdId").asText();

        mockMvc.perform(get("/api/v1/accounts/00000000006/credit-position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("PLATINUM"))
                .andExpect(jsonPath("$.activeHoldAmount").value(100.00))
                .andExpect(jsonPath("$.availableCredit").value(5449.25));

        mockMvc.perform(delete("/api/v1/accounts/00000000006/holds/{holdId}", holdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(delete("/api/v1/accounts/00000000006/holds/{holdId}", holdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(get("/api/v1/accounts/00000000006/credit-position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeHoldAmount").value(0.00))
                .andExpect(jsonPath("$.availableCredit").value(5549.25));
    }

    @Test
    void holdCreationIsIdempotentPerReference() throws Exception {
        String reference = "test-idem-" + UUID.randomUUID();
        String body = """
                {"amount":50.00,"referenceType":"AUTHORIZATION","referenceId":"%s"}
                """.formatted(reference);

        MvcResult first = mockMvc.perform(post("/api/v1/accounts/00000000008/holds")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/accounts/00000000008/holds")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("holdId").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("holdId").asText();
        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void holdReferenceCannotBeReusedWithDifferentAmount() throws Exception {
        String reference = "test-conflict-" + UUID.randomUUID();
        String firstBody = """
                {"amount":40.00,"referenceType":"MANUAL","referenceId":"%s"}
                """.formatted(reference);

        mockMvc.perform(post("/api/v1/accounts/00000000005/holds")
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/00000000005/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":60.00,"referenceType":"MANUAL","referenceId":"%s"}
                                """.formatted(reference)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_REFERENCE_CONFLICT"));
    }

    @Test
    void holdRejectsAmountAboveAvailableCredit() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/00000000001/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000.00,"referenceType":"MANUAL","referenceId":"over-limit"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_AVAILABLE_CREDIT"));
    }

    @Test
    void holdRejectsInactiveAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/00000000007/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"referenceType":"MANUAL","referenceId":"inactive-account"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void updateRejectsCreditLimitAboveProductMaximum() throws Exception {
        String body = """
                {
                  "activeStatus": "Y",
                  "currentBalance": 820.50,
                  "creditLimit": 6000.00,
                  "cashCreditLimit": 1500.00,
                  "openDate": "2015-02-14",
                  "expirationDate": "2026-02-14",
                  "reissueDate": "2026-02-14",
                  "currentCycleCredit": 120.50,
                  "currentCycleDebit": 50.00
                }
                """;

        mockMvc.perform(put("/api/v1/accounts/00000000004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDS_PRODUCT_MAX"));
    }
}
