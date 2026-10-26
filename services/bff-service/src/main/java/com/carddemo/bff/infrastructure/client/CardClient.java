package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.CardSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * card-service 出站适配器。4xx（含账户无卡）视为空列表，5xx / 超时 → {@link RemoteServiceException}（由聚合服务降级）。
 */
@Component
public class CardClient {

    private static final String SERVICE = "card-service";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CardClient(@Qualifier("cardRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<CardSummary> listByAccount(String accountId) {
        try {
            return restClient.get()
                    .uri("/api/v1/cards/by-account/{accountId}", accountId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is4xxClientError()) {
                            return List.<CardSummary>of();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP "
                                    + response.getStatusCode().value());
                        }
                        CardListBody body = read(response.getBody(), CardListBody.class);
                        if (body == null || body.items() == null) {
                            return List.<CardSummary>of();
                        }
                        return body.items().stream().map(CardBody::toSummary).toList();
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

    record CardListBody(List<CardBody> items) {
    }

    record CardBody(String cardNumber, String customerId, String embossedName,
                    LocalDate expiryDate, String activeStatus) {

        CardSummary toSummary() {
            return new CardSummary(cardNumber, customerId, embossedName, expiryDate, activeStatus);
        }
    }
}
