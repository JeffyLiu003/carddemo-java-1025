package com.carddemo.search.infrastructure.client;

import com.carddemo.search.domain.exception.RemoteServiceException;
import com.carddemo.search.domain.model.TransactionSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * transaction-service 出站适配器：账户号/卡号条件查询，仅传已使用的条件参数。
 */
class TransactionClientTest {

    private static final String BASE_URL = "http://transaction.test";
    private static final String ACCOUNT_ID = "00000000001";
    private static final String CARD_NUMBER = "0500024453765740";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
    private final TransactionClient client = new TransactionClient(restClientBuilder.build(), objectMapper);

    @Test
    void searchesByAccountId() {
        server.expect(requestTo(BASE_URL + "/api/v1/transactions?accountId=" + ACCOUNT_ID + "&page=0&size=20"))
                .andRespond(withSuccess("""
                        {"items":[{"transactionId":"0000000000000001","accountId":"00000000001",
                         "cardNumber":"0500024453765740","amount":50.47,
                         "description":"Purchase at Test Merchant",
                         "processedTimestamp":"2026-10-20T10:15:30"}],
                         "page":0,"size":20,"total":1}
                        """, MediaType.APPLICATION_JSON));

        List<TransactionSummary> transactions = client.search(null, null, ACCOUNT_ID);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).transactionId()).isEqualTo("0000000000000001");
        assertThat(transactions.get(0).accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(transactions.get(0).cardNumber()).isEqualTo(CARD_NUMBER);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo("50.47");
        assertThat(transactions.get(0).description()).isEqualTo("Purchase at Test Merchant");
        assertThat(transactions.get(0).processedTimestamp())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 15, 30));
        server.verify();
    }

    @Test
    void searchesByCardNumber() {
        server.expect(requestTo(BASE_URL + "/api/v1/transactions?cardNumber=" + CARD_NUMBER + "&page=0&size=20"))
                .andRespond(withSuccess("""
                        {"items":[],"page":0,"size":20,"total":0}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.search(null, CARD_NUMBER, null)).isEmpty();
        server.verify();
    }

    @Test
    void searchesByIdPrefix() {
        server.expect(requestTo(BASE_URL + "/api/v1/transactions?idPrefix=00000000&page=0&size=20"))
                .andRespond(withSuccess("""
                        {"items":[],"page":0,"size":20,"total":0}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.search("00000000", null, null)).isEmpty();
        server.verify();
    }

    @Test
    void returnsEmptyListOnClientError() {
        server.expect(requestTo(BASE_URL + "/api/v1/transactions?accountId=" + ACCOUNT_ID + "&page=0&size=20"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.search(null, null, ACCOUNT_ID)).isEmpty();
    }

    @Test
    void mapsServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/transactions?accountId=" + ACCOUNT_ID + "&page=0&size=20"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> client.search(null, null, ACCOUNT_ID))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("504");
    }
}
