package com.carddemo.billing.interfaces.rest;

import com.carddemo.billing.domain.exception.BusinessRuleException;
import com.carddemo.billing.domain.exception.NotFoundException;
import com.carddemo.billing.domain.exception.RemoteServiceException;
import com.carddemo.billing.domain.model.AccountCycleTotals;
import com.carddemo.billing.domain.model.AccountPosition;
import com.carddemo.billing.domain.model.AccountProductSnapshot;
import com.carddemo.billing.infrastructure.client.AccountClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账单 REST 契约：生成 201、详情、分页、全额支付、404/409/503。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatementControllerTest {

    private static final String ACCOUNT_ID = "00000000001";
    private static final String OTHER_ACCOUNT_ID = "00000000002";
    private static final String PRODUCT_CODE = "CARD_BASIC";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountClient accountClient;

    private void stubAccount(String accountId, String balance) {
        when(accountClient.creditPosition(accountId)).thenReturn(new AccountPosition(accountId, PRODUCT_CODE,
                new BigDecimal("5000.00"), new BigDecimal(balance), new BigDecimal("3060.00")));
        when(accountClient.cycleTotals(accountId)).thenReturn(new AccountCycleTotals(accountId, PRODUCT_CODE,
                new BigDecimal("200.00"), new BigDecimal("-300.00")));
        when(accountClient.findProduct(PRODUCT_CODE))
                .thenReturn(new AccountProductSnapshot(PRODUCT_CODE, new BigDecimal("0.1899")));
    }

    private static String generateBody(String accountId, String period) {
        return """
                {"accountId":"%s","period":"%s"}
                """.formatted(accountId, period);
    }

    private MvcResult generate(String accountId, String period) throws Exception {
        return mockMvc.perform(post("/api/v1/statements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(accountId, period)))
                .andReturn();
    }

    private String statementIdOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("statementId").asText();
    }

    @Test
    void generatesStatementWithCalculatedAmounts() throws Exception {
        stubAccount(ACCOUNT_ID, "1940.00");

        mockMvc.perform(post("/api/v1/statements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ACCOUNT_ID, "2026-10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statementId").isNotEmpty())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.period").value("2026-10"))
                .andExpect(jsonPath("$.cycleDate").value("2026-10-31"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-25"))
                .andExpect(jsonPath("$.openingBalance").value(2040.00))
                .andExpect(jsonPath("$.closingBalance").value(1940.00))
                .andExpect(jsonPath("$.purchases").value(200.00))
                .andExpect(jsonPath("$.payments").value(300.00))
                .andExpect(jsonPath("$.fees").value(0.00))
                .andExpect(jsonPath("$.interest").value(30.70))
                .andExpect(jsonPath("$.minimumPayment").value(58.20))
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.paidAt").isEmpty());
    }

    @Test
    void generatingSameAccountAndPeriodReturnsExistingStatement() throws Exception {
        stubAccount(ACCOUNT_ID, "1940.00");

        String first = statementIdOf(generate(ACCOUNT_ID, "2026-10"));
        String second = statementIdOf(generate(ACCOUNT_ID, "2026-10"));

        assertThat(second).isEqualTo(first);
        mockMvc.perform(get("/api/v1/statements").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void returnsStatementDetailAnd404ForUnknownStatement() throws Exception {
        stubAccount(ACCOUNT_ID, "1940.00");
        String statementId = statementIdOf(generate(ACCOUNT_ID, "2026-10"));

        mockMvc.perform(get("/api/v1/statements/{statementId}", statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statementId").value(statementId))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.minimumPayment").value(58.20))
                .andExpect(jsonPath("$.status").value("GENERATED"));

        mockMvc.perform(get("/api/v1/statements/{statementId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STATEMENT_NOT_FOUND"));
    }

    @Test
    void listsStatementsByAccountWithPagination() throws Exception {
        stubAccount(ACCOUNT_ID, "1940.00");
        stubAccount(OTHER_ACCOUNT_ID, "500.00");

        generate(ACCOUNT_ID, "2026-09");
        generate(ACCOUNT_ID, "2026-10");
        generate(OTHER_ACCOUNT_ID, "2026-10");

        mockMvc.perform(get("/api/v1/statements")
                        .param("accountId", ACCOUNT_ID)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].period").value("2026-10"));
    }

    @Test
    void paysStatementAndMarksItPaid() throws Exception {
        stubAccount(ACCOUNT_ID, "1940.00");
        String statementId = statementIdOf(generate(ACCOUNT_ID, "2026-10"));

        mockMvc.perform(post("/api/v1/statements/{statementId}/pay", statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statementId").value(statementId))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/statements/{statementId}/pay", statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(accountClient, times(1)).payBill(ACCOUNT_ID, new BigDecimal("1940.00"));
    }

    @Test
    void paysZeroBalanceStatementWithoutCallingAccount() throws Exception {
        stubAccount(ACCOUNT_ID, "0.00");
        String statementId = statementIdOf(generate(ACCOUNT_ID, "2026-10"));

        mockMvc.perform(post("/api/v1/statements/{statementId}/pay", statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingBalance").value(0.00))
                .andExpect(jsonPath("$.minimumPayment").value(0.00))
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(accountClient, never()).payBill(any(), any());
    }

    @Test
    void paymentRejectedByAccountReturns409() throws Exception {
        stubAccount(ACCOUNT_ID, "1940.00");
        doThrow(new BusinessRuleException("NOTHING_TO_PAY", "You have nothing to pay..."))
                .when(accountClient).payBill(eq(ACCOUNT_ID), any());
        String statementId = statementIdOf(generate(ACCOUNT_ID, "2026-10"));

        mockMvc.perform(post("/api/v1/statements/{statementId}/pay", statementId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_PAY"))
                .andExpect(jsonPath("$.message").value("You have nothing to pay..."));
    }

    @Test
    void generateReturns404WhenAccountMissing() throws Exception {
        when(accountClient.creditPosition(ACCOUNT_ID))
                .thenThrow(new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + ACCOUNT_ID + " not found"));

        mockMvc.perform(post("/api/v1/statements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ACCOUNT_ID, "2026-10")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void generateReturns503WhenAccountServiceUnavailable() throws Exception {
        when(accountClient.creditPosition(ACCOUNT_ID))
                .thenThrow(new RemoteServiceException("account-service is unavailable"));

        mockMvc.perform(post("/api/v1/statements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ACCOUNT_ID, "2026-10")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void generateRejectsInvalidPeriodAndMissingAccount() throws Exception {
        mockMvc.perform(post("/api/v1/statements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ACCOUNT_ID, "2026-13")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

        mockMvc.perform(post("/api/v1/statements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
