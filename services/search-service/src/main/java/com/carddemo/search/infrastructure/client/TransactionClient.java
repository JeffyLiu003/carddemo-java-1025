package com.carddemo.search.infrastructure.client;

import com.carddemo.search.domain.exception.RemoteServiceException;
import com.carddemo.search.domain.model.TransactionSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * transaction-service 出站适配器：`GET /api/v1/transactions?idPrefix=&amp;cardNumber=&amp;accountId=`
 * （未使用的条件不传参）。4xx → 空列表，5xx / 超时 → {@link RemoteServiceException}。
 */
@Component
public class TransactionClient {

    private static final String SERVICE = "transaction-service";
    private static final int PAGE_SIZE = 20;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TransactionClient(@Qualifier("transactionRestClient") RestClient restClient,
                             ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<TransactionSummary> search(String idPrefix, String cardNumber, String accountId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/transactions")
                            .queryParamIfPresent("idPrefix", Optional.ofNullable(idPrefix))
                            .queryParamIfPresent("cardNumber", Optional.ofNullable(cardNumber))
                            .queryParamIfPresent("accountId", Optional.ofNullable(accountId))
                            .queryParam("page", 0)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is4xxClientError()) {
                            return List.<TransactionSummary>of();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP "
                                    + response.getStatusCode().value());
                        }
                        TransactionPageBody body = read(response.getBody(), TransactionPageBody.class);
                        if (body == null || body.items() == null) {
                            return List.<TransactionSummary>of();
                        }
                        return body.items().stream().map(TransactionBody::toSummary).toList();
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

    record TransactionPageBody(List<TransactionBody> items) {
    }

    record TransactionBody(String transactionId, String accountId, String cardNumber,
                           BigDecimal amount, String description, LocalDateTime processedTimestamp) {

        TransactionSummary toSummary() {
            return new TransactionSummary(transactionId, accountId, cardNumber, amount, description,
                    processedTimestamp);
        }
    }
}
