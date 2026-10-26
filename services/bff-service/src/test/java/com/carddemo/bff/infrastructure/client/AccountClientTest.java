package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.NotFoundException;
import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.AccountPosition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * account-service 出站适配器：额度解析、404 → ACCOUNT_NOT_FOUND、5xx → RemoteServiceException。
 */
class AccountClientTest {

    private static final String BASE_URL = "http://account.test";
    private static final String ACCOUNT_ID = "00000000001";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
    private final AccountClient client = new AccountClient(restClientBuilder.build(), objectMapper);

    @Test
    void readsCreditPosition() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withSuccess("""
                        {"accountId":"00000000001","productCode":"CLASSIC","creditLimit":2020.00,
                         "currentBalance":1940.00,"activeHoldAmount":0.00,"availableCredit":80.00,
                         "version":3}
                        """, MediaType.APPLICATION_JSON));

        AccountPosition position = client.creditPosition(ACCOUNT_ID);

        assertThat(position.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(position.productCode()).isEqualTo("CLASSIC");
        assertThat(position.creditLimit()).isEqualByComparingTo("2020.00");
        assertThat(position.currentBalance()).isEqualByComparingTo("1940.00");
        assertThat(position.activeHoldAmount()).isEqualByComparingTo("0.00");
        assertThat(position.availableCredit()).isEqualByComparingTo("80.00");
        server.verify();
    }

    @Test
    void mapsMissingAccountToNotFound() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.creditPosition(ACCOUNT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(ACCOUNT_ID)
                .extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void mapsServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.creditPosition(ACCOUNT_ID))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("503");
    }
}
