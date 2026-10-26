package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.OfferSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * marketing-service 出站适配器：`GET /api/v1/eligibilities?accountId=&cardNumber=&customerId=`。
 * 4xx → 空（无权益也算正常），5xx / 超时 → {@link RemoteServiceException}（由聚合服务降级）。
 */
@Component
public class MarketingClient {

    private static final String SERVICE = "marketing-service";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MarketingClient(@Qualifier("marketingRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<OfferSummary> offers(String accountId, String cardNumber, String customerId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/eligibilities")
                            .queryParam("accountId", accountId)
                            .queryParam("cardNumber", cardNumber)
                            .queryParamIfPresent("customerId", Optional.ofNullable(customerId))
                            .build())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is4xxClientError()) {
                            return List.<OfferSummary>of();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP "
                                    + response.getStatusCode().value());
                        }
                        OfferListBody body = read(response.getBody(), OfferListBody.class);
                        if (body == null || body.items() == null) {
                            return List.<OfferSummary>of();
                        }
                        return body.items().stream().map(OfferBody::toSummary).toList();
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    private <T> T read(InputStream body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException ex) {
            throw new RemoteServiceException(SERVICE + " returned an unreadable body", ex);
        }
    }

    record OfferListBody(List<OfferBody> items) {
    }

    record OfferBody(String campaignId, String name, Boolean eligible, Boolean enrolled) {

        OfferSummary toSummary() {
            return new OfferSummary(campaignId, name, Boolean.TRUE.equals(eligible),
                    Boolean.TRUE.equals(enrolled));
        }
    }
}
