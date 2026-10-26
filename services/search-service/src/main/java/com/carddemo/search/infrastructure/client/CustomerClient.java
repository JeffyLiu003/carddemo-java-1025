package com.carddemo.search.infrastructure.client;

import com.carddemo.search.domain.exception.RemoteServiceException;
import com.carddemo.search.domain.model.CustomerSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * customer-service 出站适配器：`GET /api/v1/customers?page=0&amp;size=200`。
 * 4xx → 空列表，5xx / 超时 → {@link RemoteServiceException}。
 */
@Component
public class CustomerClient {

    private static final String SERVICE = "customer-service";
    private static final int PAGE_SIZE = 200;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CustomerClient(@Qualifier("customerRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<CustomerSummary> listCustomers() {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/customers")
                            .queryParam("page", 0)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is4xxClientError()) {
                            return List.<CustomerSummary>of();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP "
                                    + response.getStatusCode().value());
                        }
                        CustomerPageBody body = read(response.getBody(), CustomerPageBody.class);
                        if (body == null || body.items() == null) {
                            return List.<CustomerSummary>of();
                        }
                        return body.items().stream().map(CustomerBody::toSummary).toList();
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

    record CustomerPageBody(List<CustomerBody> items) {
    }

    record CustomerBody(String customerId, String firstName, String middleName, String lastName,
                        String ssnMasked) {

        CustomerSummary toSummary() {
            return new CustomerSummary(customerId, firstName, middleName, lastName, ssnMasked);
        }
    }
}
