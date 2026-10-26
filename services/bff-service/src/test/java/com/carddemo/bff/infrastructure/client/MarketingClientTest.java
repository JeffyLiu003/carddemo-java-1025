package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.OfferSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * marketing-service 出站适配器：权益查询，4xx → 空，5xx → RemoteServiceException。
 */
class MarketingClientTest {

    private static final String BASE_URL = "http://marketing.test";
    private static final String ACCOUNT_ID = "00000000001";
    private static final String CARD_NUMBER = "0500024453765740";
    private static final String CUSTOMER_ID = "000000001";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
    private final MarketingClient client = new MarketingClient(restClientBuilder.build(), objectMapper);

    @Test
    void readsEligibleOffers() {
        server.expect(requestTo(BASE_URL + "/api/v1/eligibilities?accountId=" + ACCOUNT_ID
                        + "&cardNumber=" + CARD_NUMBER + "&customerId=" + CUSTOMER_ID))
                .andRespond(withSuccess("""
                        {"items":[{"campaignId":"CAMP001","name":"开卡首刷礼","eligible":true,
                         "enrolled":false,"reason":null,"campaignType":"FIRST_PURCHASE_CASHBACK"}]}
                        """, MediaType.APPLICATION_JSON));

        List<OfferSummary> offers = client.offers(ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).campaignId()).isEqualTo("CAMP001");
        assertThat(offers.get(0).name()).isEqualTo("开卡首刷礼");
        assertThat(offers.get(0).eligible()).isTrue();
        assertThat(offers.get(0).enrolled()).isFalse();
        server.verify();
    }

    @Test
    void returnsEmptyListOnClientError() {
        server.expect(requestTo(BASE_URL + "/api/v1/eligibilities?accountId=" + ACCOUNT_ID
                        + "&cardNumber=" + CARD_NUMBER))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.offers(ACCOUNT_ID, CARD_NUMBER, null)).isEmpty();
    }

    @Test
    void mapsServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/eligibilities?accountId=" + ACCOUNT_ID
                        + "&cardNumber=" + CARD_NUMBER))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.offers(ACCOUNT_ID, CARD_NUMBER, null))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("500");
    }
}
