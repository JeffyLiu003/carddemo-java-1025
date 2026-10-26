package com.carddemo.bff.interfaces.rest;

import com.carddemo.bff.domain.exception.NotFoundException;
import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.AccountPosition;
import com.carddemo.bff.domain.model.CardSummary;
import com.carddemo.bff.domain.model.CustomerProfile;
import com.carddemo.bff.domain.model.OfferSummary;
import com.carddemo.bff.domain.model.RecentTransaction;
import com.carddemo.bff.infrastructure.client.AccountClient;
import com.carddemo.bff.infrastructure.client.CardClient;
import com.carddemo.bff.infrastructure.client.CustomerClient;
import com.carddemo.bff.infrastructure.client.MarketingClient;
import com.carddemo.bff.infrastructure.client.TransactionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BFF 聚合契约：全量聚合、账户 404/503、单个下游降级、无卡场景、参数缺失 400。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerOverviewControllerTest {

    private static final String ACCOUNT_ID = "00000000001";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "0500024453765740";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountClient accountClient;

    @MockBean
    private CardClient cardClient;

    @MockBean
    private CustomerClient customerClient;

    @MockBean
    private TransactionClient transactionClient;

    @MockBean
    private MarketingClient marketingClient;

    private void stubAccount() {
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, "CLASSIC",
                new BigDecimal("2020.00"), new BigDecimal("1940.00"), new BigDecimal("0.00"),
                new BigDecimal("80.00")));
    }

    private void stubCard() {
        when(cardClient.listByAccount(ACCOUNT_ID)).thenReturn(List.of(new CardSummary(CARD_NUMBER, CUSTOMER_ID,
                "Immanuel Kessler", LocalDate.of(2028, 12, 31), "Y")));
    }

    private void stubCustomer() {
        when(customerClient.findById(CUSTOMER_ID)).thenReturn(Optional.of(new CustomerProfile(CUSTOMER_ID,
                "Immanuel", "Kessler", "***-**-1234", 700)));
    }

    private void stubTransactions() {
        when(transactionClient.recentByAccount(ACCOUNT_ID, 5)).thenReturn(List.of(
                new RecentTransaction("0000000000000001", new BigDecimal("50.47"),
                        "Purchase at Test Merchant", LocalDateTime.of(2026, 10, 20, 10, 0))));
    }

    private void stubOffers() {
        when(marketingClient.offers(ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID)).thenReturn(List.of(
                new OfferSummary("CAMP001", "开卡首刷礼", true, false)));
    }

    @Test
    void aggregatesFullCustomerOverview() throws Exception {
        stubAccount();
        stubCard();
        stubCustomer();
        stubTransactions();
        stubOffers();

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.position.productCode").value("CLASSIC"))
                .andExpect(jsonPath("$.position.creditLimit").value(2020.00))
                .andExpect(jsonPath("$.position.currentBalance").value(1940.00))
                .andExpect(jsonPath("$.position.activeHoldAmount").value(0.00))
                .andExpect(jsonPath("$.position.availableCredit").value(80.00))
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.cards[0].cardNumber").value(CARD_NUMBER))
                .andExpect(jsonPath("$.cards[0].customerId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.cards[0].activeStatus").value("Y"))
                .andExpect(jsonPath("$.cards[0].expiryDate").value("2028-12-31"))
                .andExpect(jsonPath("$.customer.customerId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.customer.firstName").value("Immanuel"))
                .andExpect(jsonPath("$.customer.lastName").value("Kessler"))
                .andExpect(jsonPath("$.customer.ssnMasked").value("***-**-1234"))
                .andExpect(jsonPath("$.recentTransactions.length()").value(1))
                .andExpect(jsonPath("$.recentTransactions[0].transactionId").value("0000000000000001"))
                .andExpect(jsonPath("$.recentTransactions[0].amount").value(50.47))
                .andExpect(jsonPath("$.recentTransactions[0].description")
                        .value("Purchase at Test Merchant"))
                .andExpect(jsonPath("$.recentTransactions[0].processedTimestamp")
                        .value("2026-10-20T10:00:00"))
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].campaignId").value("CAMP001"))
                .andExpect(jsonPath("$.offers[0].name").value("开卡首刷礼"))
                .andExpect(jsonPath("$.offers[0].eligible").value(true))
                .andExpect(jsonPath("$.offers[0].enrolled").value(false))
                .andExpect(jsonPath("$.degraded.length()").value(0));
    }

    @Test
    void returns404WhenAccountNotFound() throws Exception {
        when(accountClient.creditPosition(ACCOUNT_ID)).thenThrow(
                new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + ACCOUNT_ID + " not found"));

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account " + ACCOUNT_ID + " not found"));
    }

    @Test
    void returns503WhenAccountServiceUnavailable() throws Exception {
        when(accountClient.creditPosition(ACCOUNT_ID))
                .thenThrow(new RemoteServiceException("account-service is unavailable"));

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        verify(cardClient, never()).listByAccount(anyString());
    }

    @Test
    void degradesMarketingWhileKeepingOtherNodes() throws Exception {
        stubAccount();
        stubCard();
        stubCustomer();
        stubTransactions();
        when(marketingClient.offers(ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID))
                .thenThrow(new RemoteServiceException("marketing-service is unavailable"));

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position.availableCredit").value(80.00))
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.customer.customerId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.recentTransactions.length()").value(1))
                .andExpect(jsonPath("$.offers.length()").value(0))
                .andExpect(jsonPath("$.degraded.length()").value(1))
                .andExpect(jsonPath("$.degraded[0]").value("marketing-service"));
    }

    @Test
    void degradesCardServiceAndDropsCustomerAndOffers() throws Exception {
        stubAccount();
        when(cardClient.listByAccount(ACCOUNT_ID))
                .thenThrow(new RemoteServiceException("card-service is unavailable"));
        stubTransactions();

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position.productCode").value("CLASSIC"))
                .andExpect(jsonPath("$.cards.length()").value(0))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.offers.length()").value(0))
                .andExpect(jsonPath("$.recentTransactions.length()").value(1))
                .andExpect(jsonPath("$.degraded.length()").value(1))
                .andExpect(jsonPath("$.degraded[0]").value("card-service"));

        verify(customerClient, never()).findById(anyString());
        verify(marketingClient, never()).offers(anyString(), anyString(), anyString());
    }

    @Test
    void degradesCustomerAndTransactionIndependently() throws Exception {
        stubAccount();
        stubCard();
        when(customerClient.findById(CUSTOMER_ID))
                .thenThrow(new RemoteServiceException("customer-service is unavailable"));
        when(transactionClient.recentByAccount(ACCOUNT_ID, 5))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));
        when(marketingClient.offers(ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.recentTransactions.length()").value(0))
                .andExpect(jsonPath("$.offers.length()").value(0))
                .andExpect(jsonPath("$.degraded.length()").value(2))
                .andExpect(jsonPath("$.degraded[0]").value("customer-service"))
                .andExpect(jsonPath("$.degraded[1]").value("transaction-service"));
    }

    @Test
    void returnsAccountWithoutCustomerWhenNoCards() throws Exception {
        stubAccount();
        when(cardClient.listByAccount(ACCOUNT_ID)).thenReturn(List.of());
        stubTransactions();

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(0))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.offers.length()").value(0))
                .andExpect(jsonPath("$.recentTransactions.length()").value(1))
                .andExpect(jsonPath("$.degraded.length()").value(0));

        verify(customerClient, never()).findById(anyString());
        verify(marketingClient, never()).offers(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsMissingAndMalformedAccountId() throws Exception {
        mockMvc.perform(get("/api/v1/bff/customer-overview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", "account-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void treatsEmptyCustomerLookupAsMissingCustomer() throws Exception {
        stubAccount();
        stubCard();
        when(customerClient.findById(CUSTOMER_ID)).thenReturn(Optional.empty());
        when(transactionClient.recentByAccount(ACCOUNT_ID, 5)).thenReturn(List.of());
        stubOffers();

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.degraded.length()").value(0));

        verify(transactionClient).recentByAccount(ACCOUNT_ID, 5);
    }

    @Test
    void requestsFiveRecentTransactions() throws Exception {
        stubAccount();
        stubCard();
        stubCustomer();
        stubTransactions();
        stubOffers();

        mockMvc.perform(get("/api/v1/bff/customer-overview").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk());

        verify(transactionClient).recentByAccount(ACCOUNT_ID, 5);
        verify(marketingClient).offers(ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);
        verify(accountClient).creditPosition(ACCOUNT_ID);
    }
}
