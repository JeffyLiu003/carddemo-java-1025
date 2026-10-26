package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.RecentTransaction;
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

/**
 * transaction-service 出站适配器：按账户取最新交易。4xx → 空，5xx / 超时 → {@link RemoteServiceException}。
 */
@Component
public class TransactionClient {

    private static final String SERVICE = "transaction-service";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TransactionClient(@Qualifier("transactionRestClient") RestClient restClient,
                             ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<RecentTransaction> recentByAccount(String accountId, int size) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/transactions")
                            .queryParam("accountId", accountId)
                            .queryParam("page", 0)
                            .queryParam("size", size)
                            .build())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is4xxClientError()) {
                            return List.<RecentTransaction>of();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP "
                                    + response.getStatusCode().value());
                        }
                        TransactionListBody body = read(response.getBody(), TransactionListBody.class);
                        if (body == null || body.items() == null) {
                            return List.<RecentTransaction>of();
                        }
                        return body.items().stream().map(TransactionBody::toRecent).toList();
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

    record TransactionListBody(List<TransactionBody> items) {
    }

    record TransactionBody(String transactionId, BigDecimal amount, String description,
                           LocalDateTime processedTimestamp) {

        RecentTransaction toRecent() {
            return new RecentTransaction(transactionId, amount, description, processedTimestamp);
        }
    }
}
