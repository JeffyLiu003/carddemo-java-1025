package com.carddemo.search.interfaces.rest;

import com.carddemo.search.domain.exception.RemoteServiceException;
import com.carddemo.search.domain.model.CustomerSummary;
import com.carddemo.search.domain.model.TransactionSummary;
import com.carddemo.search.infrastructure.client.CustomerClient;
import com.carddemo.search.infrastructure.client.TransactionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一搜索契约：客户搜索、账户/卡号交易搜索、ALL 合并、非法 type、limit 边界、下游降级与 503。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTest {

    private static final String ACCOUNT_ID = "00000000001";
    private static final String CARD_NUMBER = "0500024453765740";
    private static final String TRANSACTION_ID = "0000000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerClient customerClient;

    @MockBean
    private TransactionClient transactionClient;

    private void stubCustomers() {
        when(customerClient.listCustomers()).thenReturn(List.of(
                new CustomerSummary("000000001", "Immanuel", "H", "Kessler", "***-**-1234"),
                new CustomerSummary("000000002", "Barbara", null, "Parker", "***-**-5678")));
    }

    private void stubAccountTransactionSearch(String accountId) {
        when(transactionClient.search(isNull(), isNull(), eq(accountId)))
                .thenReturn(List.of(transaction(accountId, CARD_NUMBER)));
    }

    private void stubCardTransactionSearch(String cardNumber) {
        when(transactionClient.search(isNull(), eq(cardNumber), isNull()))
                .thenReturn(List.of(transaction(ACCOUNT_ID, cardNumber)));
    }

    private static TransactionSummary transaction(String accountId, String cardNumber) {
        return new TransactionSummary(TRANSACTION_ID, accountId, cardNumber,
                new BigDecimal("50.47"), "Purchase at Test Merchant",
                LocalDateTime.of(2026, 10, 20, 10, 15, 30));
    }

    @Test
    void searchesCustomersByNameCaseInsensitively() throws Exception {
        stubCustomers();

        mockMvc.perform(get("/api/v1/search").param("q", "kEss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("CUSTOMER"))
                .andExpect(jsonPath("$.items[0].id").value("000000001"))
                .andExpect(jsonPath("$.items[0].title").value("Immanuel H Kessler"))
                .andExpect(jsonPath("$.items[0].subtitle").value("000000001"))
                .andExpect(jsonPath("$.items[0].reference").value("***-**-1234"))
                .andExpect(jsonPath("$.degraded.length()").value(0));

        verify(transactionClient, never()).search(any(), any(), any());
    }

    @Test
    void searchesCustomersByCustomerId() throws Exception {
        stubCustomers();

        mockMvc.perform(get("/api/v1/search").param("q", "000000002").param("type", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("000000002"))
                .andExpect(jsonPath("$.items[0].title").value("Barbara Parker"));

        verify(transactionClient, never()).search(any(), any(), any());
    }

    @Test
    void searchesTransactionsByAccountId() throws Exception {
        stubAccountTransactionSearch(ACCOUNT_ID);

        mockMvc.perform(get("/api/v1/search").param("q", ACCOUNT_ID).param("type", "TRANSACTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TRANSACTION"))
                .andExpect(jsonPath("$.items[0].id").value(TRANSACTION_ID))
                .andExpect(jsonPath("$.items[0].title").value("Purchase at Test Merchant"))
                .andExpect(jsonPath("$.items[0].subtitle").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.items[0].amount").value(50.47))
                .andExpect(jsonPath("$.items[0].reference").value(CARD_NUMBER))
                .andExpect(jsonPath("$.degraded.length()").value(0));

        verify(transactionClient).search(isNull(), isNull(), eq(ACCOUNT_ID));
        verify(customerClient, never()).listCustomers();
    }

    @Test
    void searchesTransactionsByCardNumber() throws Exception {
        stubCardTransactionSearch(CARD_NUMBER);

        mockMvc.perform(get("/api/v1/search").param("q", CARD_NUMBER).param("type", "TRANSACTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(TRANSACTION_ID))
                .andExpect(jsonPath("$.items[0].reference").value(CARD_NUMBER));

        verify(transactionClient).search(isNull(), eq(CARD_NUMBER), isNull());
    }

    @Test
    void mergesCustomerAndTransactionHitsForAllType() throws Exception {
        when(customerClient.listCustomers()).thenReturn(List.of(
                new CustomerSummary(ACCOUNT_ID, "Immanuel", "H", "Kessler", "***-**-1234")));
        stubAccountTransactionSearch(ACCOUNT_ID);

        mockMvc.perform(get("/api/v1/search").param("q", ACCOUNT_ID).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("CUSTOMER"))
                .andExpect(jsonPath("$.items[1].type").value("TRANSACTION"))
                .andExpect(jsonPath("$.items[1].id").value(TRANSACTION_ID))
                .andExpect(jsonPath("$.degraded.length()").value(0));
    }

    @Test
    void skipsTransactionLookupForFreeTextQuery() throws Exception {
        stubCustomers();

        mockMvc.perform(get("/api/v1/search").param("q", "Parker").param("type", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("000000002"));

        verify(transactionClient, never()).search(any(), any(), any());
    }

    @Test
    void rejectsInvalidSearchType() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "Kessler").param("type", "OFFERS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_TYPE"));

        verify(customerClient, never()).listCustomers();
        verify(transactionClient, never()).search(any(), any(), any());
    }

    @Test
    void rejectsLimitOutsideOneToFifty() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "Kessler").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/search").param("q", "Kessler").param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void acceptsLimitBoundaries() throws Exception {
        stubCustomers();

        mockMvc.perform(get("/api/v1/search").param("q", "Kessler").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/search").param("q", "Kessler").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void truncatesItemsToLimitButKeepsTotal() throws Exception {
        stubCustomers();

        mockMvc.perform(get("/api/v1/search").param("q", "a").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("000000001"));
    }

    @Test
    void requiresQueryString() throws Exception {
        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/search").param("q", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void degradesTransactionSearchForAllType() throws Exception {
        stubCustomers();
        when(transactionClient.search(isNull(), isNull(), eq(ACCOUNT_ID)))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(get("/api/v1/search").param("q", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.degraded.length()").value(1))
                .andExpect(jsonPath("$.degraded[0]").value("transaction-service"));
    }

    @Test
    void returns503WhenTransactionTypeDownstreamFails() throws Exception {
        when(transactionClient.search(any(), any(), any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(get("/api/v1/search").param("q", ACCOUNT_ID).param("type", "TRANSACTION"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void returns503WhenCustomerTypeDownstreamFails() throws Exception {
        when(customerClient.listCustomers())
                .thenThrow(new RemoteServiceException("customer-service is unavailable"));

        mockMvc.perform(get("/api/v1/search").param("q", "Kessler").param("type", "CUSTOMER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void returns503WhenAllTypeLosesBothDownstreams() throws Exception {
        when(customerClient.listCustomers())
                .thenThrow(new RemoteServiceException("customer-service is unavailable"));
        when(transactionClient.search(any(), any(), any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        mockMvc.perform(get("/api/v1/search").param("q", ACCOUNT_ID).param("type", "ALL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void acceptsLowercaseTypeValue() throws Exception {
        stubCustomers();

        mockMvc.perform(get("/api/v1/search").param("q", "Parker").param("type", "customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        verify(customerClient).listCustomers();
        verify(transactionClient, never()).search(anyString(), anyString(), anyString());
    }
}
