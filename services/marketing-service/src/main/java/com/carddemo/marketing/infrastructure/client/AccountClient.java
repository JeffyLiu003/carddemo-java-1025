package com.carddemo.marketing.infrastructure.client;

import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.exception.RemoteServiceException;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * account-service 出站适配器（设计 §3.2）。
 *
 * <p>失败语义：credit-position 404 → {@link NotFoundException}（ACCOUNT_NOT_FOUND）；
 * 贷记的 404/409/5xx/超时 → {@link RemoteServiceException}——返现评估据此保留 PENDING 供重试。
 */
@Component
public class AccountClient {

    private static final String SERVICE = "account-service";
    private static final String SOURCE_TYPE_CASHBACK = "CASHBACK";
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AccountClient(@Qualifier("accountRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /api/v1/accounts/{accountId}/credit-position：产品等级决定资格，activeStatus 决定可否报名/返现。
     */
    public AccountPosition creditPosition(String accountId) {
        try {
            return restClient.get()
                    .uri("/api/v1/accounts/{accountId}/credit-position", accountId)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            throw accountNotFound(accountId);
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP " + status);
                        }
                        return read(response.getBody(), AccountPositionBody.class).toPosition(accountId);
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    /**
     * POST /api/v1/accounts/{accountId}/credits：creditId 幂等（用 rewardId），重复调用不重复冲减欠款。
     */
    public void credit(String accountId, String creditId, BigDecimal amount, String description) {
        try {
            restClient.post()
                    .uri("/api/v1/accounts/{accountId}/credits", accountId)
                    .body(new CreditBody(creditId, SOURCE_TYPE_CASHBACK,
                            amount.setScale(2, RoundingMode.HALF_UP), description))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP "
                                    + response.getStatusCode().value() + " crediting account " + accountId);
                        }
                        return (Void) null;
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

    private static NotFoundException accountNotFound(String accountId) {
        return new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + accountId + " not found");
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * activeStatus 缺省视为可用（account-service 未暴露该字段时的保守解释：不因缺失阻止业务）。
     */
    record AccountPositionBody(String accountId, String productCode, BigDecimal currentBalance,
                               BigDecimal availableCredit, Boolean activeStatus) {

        AccountPosition toPosition(String fallbackAccountId) {
            return new AccountPosition(
                    accountId == null ? fallbackAccountId : accountId,
                    productCode,
                    amount(currentBalance),
                    amount(availableCredit),
                    activeStatus == null || activeStatus);
        }
    }

    record CreditBody(String creditId, String sourceType, BigDecimal amount, String description) {
    }
}
