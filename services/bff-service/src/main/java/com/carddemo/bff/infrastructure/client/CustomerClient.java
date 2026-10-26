package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.CustomerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * customer-service 出站适配器。4xx（客户不存在）→ 空，5xx / 超时 → {@link RemoteServiceException}（由聚合服务降级）。
 * SSN 只透传掩码字段，不请求原文。
 */
@Component
public class CustomerClient {

    private static final String SERVICE = "customer-service";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CustomerClient(@Qualifier("customerRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public Optional<CustomerProfile> findById(String customerId) {
        try {
            return restClient.get()
                    .uri("/api/v1/customers/{customerId}", customerId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is4xxClientError()) {
                            return Optional.<CustomerProfile>empty();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP "
                                    + response.getStatusCode().value());
                        }
                        CustomerBody body = read(response.getBody(), CustomerBody.class);
                        return body == null ? Optional.<CustomerProfile>empty() : Optional.of(body.toProfile());
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

    record CustomerBody(String customerId, String firstName, String lastName,
                        String ssnMasked, Integer ficoScore) {

        CustomerProfile toProfile() {
            return new CustomerProfile(customerId, firstName, lastName, ssnMasked, ficoScore);
        }
    }
}
