package com.carddemo.billing.infrastructure.client;

import com.carddemo.billing.domain.exception.BusinessRuleException;
import com.carddemo.billing.domain.exception.NotFoundException;
import com.carddemo.billing.domain.exception.RemoteServiceException;
import com.carddemo.billing.domain.model.AccountCycleTotals;
import com.carddemo.billing.domain.model.AccountPosition;
import com.carddemo.billing.domain.model.AccountProductSnapshot;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * account-service 出站适配器：额度/周期累计/产品 APR 解析，404 映射 404，409 透传 code/message，5xx 映射 503。
 */
class AccountClientTest {

    private static final String BASE_URL = "http://account.test";
    private static final String ACCOUNT_ID = "00000000001";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final AccountClient client = new AccountClient(restClientBuilder.build(), objectMapper);

    @Test
    void readsCreditPosition() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withSuccess("""
                        {"accountId":"00000000001","productCode":"CARD_BASIC","creditLimit":5000.00,
                         "currentBalance":1940.00,"activeHoldAmount":0.00,"availableCredit":3060.00}
                        """, MediaType.APPLICATION_JSON));

        AccountPosition position = client.creditPosition(ACCOUNT_ID);

        assertThat(position.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(position.productCode()).isEqualTo("CARD_BASIC");
        assertThat(position.creditLimit()).isEqualByComparingTo("5000.00");
        assertThat(position.currentBalance()).isEqualByComparingTo("1940.00");
        assertThat(position.availableCredit()).isEqualByComparingTo("3060.00");
        server.verify();
    }

    @Test
    void mapsMissingAccountToNotFound() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.creditPosition(ACCOUNT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void mapsCreditPositionServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.creditPosition(ACCOUNT_ID))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("500");
    }

    @Test
    void readsCycleTotals() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID))
                .andRespond(withSuccess("""
                        {"accountId":"00000000001","productCode":"CARD_BASIC","activeStatus":"Y",
                         "currentBalance":1940.00,"creditLimit":5000.00,"cashCreditLimit":1000.00,
                         "currentCycleCredit":200.00,"currentCycleDebit":-300.00,"version":3}
                        """, MediaType.APPLICATION_JSON));

        AccountCycleTotals cycle = client.cycleTotals(ACCOUNT_ID);

        assertThat(cycle.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(cycle.productCode()).isEqualTo("CARD_BASIC");
        assertThat(cycle.currentCycleCredit()).isEqualByComparingTo("200.00");
        assertThat(cycle.currentCycleDebit()).isEqualByComparingTo("-300.00");
        server.verify();
    }

    @Test
    void readsProductApr() {
        server.expect(requestTo(BASE_URL + "/api/v1/account-products/CARD_BASIC"))
                .andRespond(withSuccess("""
                        {"productCode":"CARD_BASIC","name":"Basic Card","category":"CREDIT",
                         "apr":0.1899,"active":true}
                        """, MediaType.APPLICATION_JSON));

        AccountProductSnapshot product = client.findProduct("CARD_BASIC");

        assertThat(product.productCode()).isEqualTo("CARD_BASIC");
        assertThat(product.apr()).isEqualByComparingTo("0.1899");
        server.verify();
    }

    @Test
    void mapsMissingProductToNotFound() {
        server.expect(requestTo(BASE_URL + "/api/v1/account-products/UNKNOWN"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.findProduct("UNKNOWN"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void postsBillPayment() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/payments"))
                .andExpect(jsonPath("$.amount").value(1940.00))
                .andRespond(withSuccess("""
                        {"accountId":"00000000001","currentBalance":0.00}
                        """, MediaType.APPLICATION_JSON));

        client.payBill(ACCOUNT_ID, new BigDecimal("1940.00"));

        server.verify();
    }

    @Test
    void propagatesConflictCodeAndMessage() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/payments"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"NOTHING_TO_PAY","message":"You have nothing to pay..."}
                                """));

        assertThatThrownBy(() -> client.payBill(ACCOUNT_ID, new BigDecimal("1940.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You have nothing to pay...")
                .extracting("code").isEqualTo("NOTHING_TO_PAY");
    }

    @Test
    void mapsPaymentServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/payments"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.payBill(ACCOUNT_ID, new BigDecimal("10.00")))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("502");
    }
}
