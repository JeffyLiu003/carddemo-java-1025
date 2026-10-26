package com.carddemo.payment.interfaces.rest;

import com.carddemo.payment.domain.exception.RemoteServiceException;
import com.carddemo.payment.domain.model.AccountSnapshot;
import com.carddemo.payment.infrastructure.client.AccountClient;
import com.carddemo.payment.infrastructure.client.TransactionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付指令端到端用例：H2 + Flyway + MockMvc；account-service / transaction-service 用 @MockBean 替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentOrderControllerTest {

    private static final String ACCOUNT_ID = "00000000006";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountClient accountClient;

    @MockBean
    private TransactionClient transactionClient;

    @BeforeEach
    void stubDownstream() {
        when(accountClient.findAccount(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountSnapshot(ACCOUNT_ID, "Y")));
        when(transactionClient.postPayment(anyString(), anyString(), any(BigDecimal.class), anyString(),
                anyString())).thenReturn("BATCH-0001");
    }

    @Test
    void createsScreenedPaymentOrder() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("pay-1001", "Acme Utility", "120.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.idempotencyKey").value("pay-1001"))
                .andExpect(jsonPath("$.debtorAccountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.creditorName").value("Acme Utility"))
                .andExpect(jsonPath("$.creditorAccountNumber").value("1234567890123456"))
                .andExpect(jsonPath("$.creditorBankCode").value("BOFAUS3N"))
                .andExpect(jsonPath("$.amount").value(120.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.paymentType").value("ACH"))
                .andExpect(jsonPath("$.status").value("SCREENED"))
                .andExpect(jsonPath("$.reference").value("INV-2026-10"))
                .andExpect(jsonPath("$.description").value("Utility bill"))
                .andExpect(jsonPath("$.rejectReason").isEmpty())
                .andExpect(jsonPath("$.transactionBatchId").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createsRejectedPaymentOrderWhenCreditorIsMissingOrDenied() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("pay-1002", " ", "120.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("CREDITOR_NAME_MISSING"));

        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("pay-1003", "ACME FRAUD LTD", "120.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("CREDITOR_BLOCKED"));
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalPaymentOrder() throws Exception {
        String first = createPayment("pay-1004", "Acme Utility", "120.00");
        String second = createPayment("pay-1004", "Acme Utility", "120.00");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        createPayment("pay-1005", "Acme Utility", "120.00");

        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("pay-1005", "Acme Utility", "130.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void returns400WhenIdempotencyKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"debtorAccountId":"00000000006","creditorName":"Acme Utility",
                                 "amount":120.00,"currency":"USD","paymentType":"ACH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidAmountReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("pay-1006", "Acme Utility", "0.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_AMOUNT"));
    }

    @Test
    void invalidPaymentTypeReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"pay-1007","debtorAccountId":"00000000006",
                                 "creditorName":"Acme Utility","amount":120.00,"currency":"USD",
                                 "paymentType":"CASH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_TYPE"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        when(accountClient.findAccount("99999999999")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"pay-1008","debtorAccountId":"99999999999",
                                 "creditorName":"Acme Utility","amount":120.00,"currency":"USD",
                                 "paymentType":"ACH"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void inactiveAccountReturns409() throws Exception {
        when(accountClient.findAccount(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountSnapshot(ACCOUNT_ID, "N")));

        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("pay-1009", "Acme Utility", "120.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void accountServiceUnavailableReturns503() throws Exception {
        when(accountClient.findAccount(ACCOUNT_ID))
                .thenThrow(new RemoteServiceException("account-service is unavailable"));

        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("pay-1010", "Acme Utility", "120.00")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void acceptMovesPaymentOrderToAcceptedAndIsIdempotent() throws Exception {
        String paymentId = createPayment("pay-1011", "Acme Utility", "120.00");

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/accept", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/accept", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void acceptOfRejectedPaymentReturns409() throws Exception {
        String paymentId = createPayment("pay-1012", "ACME FRAUD LTD", "120.00");

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/accept", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_SCREENED"));
    }

    @Test
    void settlePostsPaymentBatchAndIsIdempotent() throws Exception {
        String paymentId = createPayment("pay-1013", "Acme Utility", "120.00");
        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/accept", paymentId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/settle", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.transactionBatchId").value("BATCH-0001"))
                .andExpect(jsonPath("$.settledAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/settle", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionBatchId").value("BATCH-0001"));

        verify(transactionClient, times(1)).postPayment(eq("payment:" + paymentId), eq(ACCOUNT_ID),
                eq(new BigDecimal("120.00")), eq("USD"), eq("Utility bill"));
    }

    @Test
    void settleOfScreenedPaymentReturns409() throws Exception {
        String paymentId = createPayment("pay-1014", "Acme Utility", "120.00");

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/settle", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ACCEPTED"));
    }

    @Test
    void settleWhenTransactionServiceIsDownReturns503AndKeepsStatus() throws Exception {
        String paymentId = createPayment("pay-1015", "Acme Utility", "120.00");
        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/accept", paymentId))
                .andExpect(status().isOk());
        when(transactionClient.postPayment(anyString(), anyString(), any(BigDecimal.class), anyString(),
                anyString())).thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/settle", paymentId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from payment_order where payment_id = ?", String.class, paymentId))
                .isEqualTo("ACCEPTED");
    }

    @Test
    void returnOfSettledPaymentReversesBatch() throws Exception {
        String paymentId = settledPayment("pay-1016");

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/return", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/return", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        verify(transactionClient, times(1)).reverseBatch("BATCH-0001");
    }

    @Test
    void returnOfScreenedPaymentReturns409() throws Exception {
        String paymentId = createPayment("pay-1017", "Acme Utility", "120.00");

        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/return", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_RETURNABLE"));
    }

    @Test
    void returnsPaymentDetailAnd404ForUnknownPayment() throws Exception {
        String paymentId = createPayment("pay-1018", "Acme Utility", "120.00");

        mockMvc.perform(get("/api/v1/payment-orders/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("SCREENED"));

        mockMvc.perform(get("/api/v1/payment-orders/{paymentId}", "unknown-payment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void listsPaymentOrdersWithPaginationAndFilters() throws Exception {
        createPayment("pay-1019", "Acme Utility", "120.00");
        createPayment("pay-1020", "ACME FRAUD LTD", "45.00");

        mockMvc.perform(get("/api/v1/payment-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/payment-orders").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/payment-orders").param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/payment-orders").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/payment-orders").param("accountId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void invalidStatusFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/payment-orders").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS"));
    }

    private String settledPayment(String idempotencyKey) throws Exception {
        String paymentId = createPayment(idempotencyKey, "Acme Utility", "120.00");
        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/accept", paymentId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payment-orders/{paymentId}/settle", paymentId))
                .andExpect(status().isOk());
        return paymentId;
    }

    private String createPayment(String idempotencyKey, String creditorName, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(idempotencyKey, creditorName, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("paymentId").asText();
    }

    private static String request(String idempotencyKey, String creditorName, String amount) {
        return """
                {"idempotencyKey":"%s","debtorAccountId":"%s","creditorName":"%s",
                 "creditorAccountNumber":"1234567890123456","creditorBankCode":"BOFAUS3N",
                 "amount":%s,"currency":"USD","paymentType":"ACH",
                 "reference":"INV-2026-10","description":"Utility bill"}
                """.formatted(idempotencyKey, ACCOUNT_ID, creditorName, amount);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
