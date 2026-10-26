package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.NotFoundException;
import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.AccountPosition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

/**
 * account-service 出站适配器：额度视图是概览的骨架，失败语义不透降级。
 * 404 → ACCOUNT_NOT_FOUND（HTTP 404）；其他非 2xx / 超时 / 连接失败 → {@link RemoteServiceException}（HTTP 503）。
 */
@Component
public class AccountClient {

    private static final String SERVICE = "account-service";
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AccountClient(@Qualifier("accountRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public AccountPosition creditPosition(String accountId) {
        try {
            return restClient.get()
                    .uri("/api/v1/accounts/{accountId}/credit-position", accountId)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            throw new NotFoundException("ACCOUNT_NOT_FOUND",
                                    "Account " + accountId + " not found");
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP " + status);
                        }
                        return read(response.getBody(), CreditPositionBody.class).toPosition(accountId);
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

    record CreditPositionBody(String accountId, String productCode, BigDecimal creditLimit,
                              BigDecimal currentBalance, BigDecimal activeHoldAmount,
                              BigDecimal availableCredit) {

        AccountPosition toPosition(String requestedAccountId) {
            String resolvedId = accountId == null || accountId.isBlank() ? requestedAccountId : accountId;
            return new AccountPosition(resolvedId, productCode, amount(creditLimit), amount(currentBalance),
                    amount(activeHoldAmount), amount(availableCredit));
        }
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
