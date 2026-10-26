package com.carddemo.marketing.infrastructure.client;

import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.exception.RemoteServiceException;
import com.carddemo.marketing.domain.model.AccountPosition;
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
 * account-service 出站适配器：credit-position 解析、贷记请求体（creditId 幂等键 + CASHBACK 来源），
 * 404/409/5xx 一律映射 {@link RemoteServiceException}（返现保留 PENDING 重试）。
 */
class AccountClientTest {

    private static final String BASE_URL = "http://account.test";
    private static final String ACCOUNT_ID = "00000000006";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final AccountClient client = new AccountClient(restClientBuilder.build(), objectMapper);

    @Test
    void readsCreditPositionWithProductAndActiveStatus() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withSuccess("""
                        {"accountId":"00000000006","productCode":"PLATINUM","creditLimit":25000.00,
                         "currentBalance":1940.00,"activeHoldAmount":0.00,"availableCredit":23060.00,
                         "activeStatus":true}
                        """, MediaType.APPLICATION_JSON));

        AccountPosition position = client.creditPosition(ACCOUNT_ID);

        assertThat(position.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(position.productCode()).isEqualTo("PLATINUM");
        assertThat(position.currentBalance()).isEqualByComparingTo("1940.00");
        assertThat(position.availableCredit()).isEqualByComparingTo("23060.00");
        assertThat(position.activeStatus()).isTrue();
        server.verify();
    }

    @Test
    void treatsMissingActiveStatusAsActive() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credit-position"))
                .andRespond(withSuccess("""
                        {"accountId":"00000000006","productCode":"PLATINUM","currentBalance":0.00,
                         "availableCredit":25000.00}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.creditPosition(ACCOUNT_ID).activeStatus()).isTrue();
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
    void postsCashbackCreditWithIdempotentCreditId() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credits"))
                .andExpect(jsonPath("$.creditId").value("reward-1"))
                .andExpect(jsonPath("$.sourceType").value("CASHBACK"))
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.description").value("PLATINUM_3M_CASHBACK cashback"))
                .andRespond(withSuccess("""
                        {"accountId":"00000000006","currentBalance":1910.00}
                        """, MediaType.APPLICATION_JSON));

        client.credit(ACCOUNT_ID, "reward-1", new BigDecimal("30.00"), "PLATINUM_3M_CASHBACK cashback");

        server.verify();
    }

    @Test
    void mapsCreditConflictsAndServerErrorsToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credits"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> client.credit(ACCOUNT_ID, "reward-1", new BigDecimal("30.00"), "cashback"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("409");

        server.reset();
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credits"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.credit(ACCOUNT_ID, "reward-1", new BigDecimal("30.00"), "cashback"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("404");

        server.reset();
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/" + ACCOUNT_ID + "/credits"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> client.credit(ACCOUNT_ID, "reward-1", new BigDecimal("30.00"), "cashback"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("502");
    }
}
