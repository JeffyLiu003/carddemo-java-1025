package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.CardSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * card-service 出站适配器：4xx → 空列表，5xx → RemoteServiceException。
 */
class CardClientTest {

    private static final String BASE_URL = "http://card.test";
    private static final String ACCOUNT_ID = "00000000001";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
    private final CardClient client = new CardClient(restClientBuilder.build(), objectMapper);

    @Test
    void readsCardsOfAccount() {
        server.expect(requestTo(BASE_URL + "/api/v1/cards/by-account/" + ACCOUNT_ID))
                .andRespond(withSuccess("""
                        {"items":[{"cardNumber":"0500024453765740","accountId":"00000000001",
                         "customerId":"000000001","embossedName":"Immanuel Kessler",
                         "expiryDate":"2028-12-31","activeStatus":"Y","version":1}]}
                        """, MediaType.APPLICATION_JSON));

        List<CardSummary> cards = client.listByAccount(ACCOUNT_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).cardNumber()).isEqualTo("0500024453765740");
        assertThat(cards.get(0).customerId()).isEqualTo("000000001");
        assertThat(cards.get(0).embossedName()).isEqualTo("Immanuel Kessler");
        assertThat(cards.get(0).expiryDate()).isEqualTo(LocalDate.of(2028, 12, 31));
        assertThat(cards.get(0).activeStatus()).isEqualTo("Y");
        server.verify();
    }

    @Test
    void returnsEmptyListOnClientError() {
        server.expect(requestTo(BASE_URL + "/api/v1/cards/by-account/" + ACCOUNT_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.listByAccount(ACCOUNT_ID)).isEmpty();
    }

    @Test
    void mapsServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/cards/by-account/" + ACCOUNT_ID))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.listByAccount(ACCOUNT_ID))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("500");
    }
}
