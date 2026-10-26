package com.carddemo.billing.infrastructure.client;

import com.carddemo.billing.domain.exception.BusinessRuleException;
import com.carddemo.billing.domain.exception.NotFoundException;
import com.carddemo.billing.domain.exception.RemoteServiceException;
import com.carddemo.billing.domain.model.AccountCycleTotals;
import com.carddemo.billing.domain.model.AccountPosition;
import com.carddemo.billing.domain.model.AccountProductSnapshot;
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
 * account-service 出站适配器。
 * 失败语义（docs/design/03-service-decomposition.md §4）：404 → 实体不存在；409 → 透传下游 code/message；
 * 5xx / 超时 / 连接失败 → {@link RemoteServiceException}（HTTP 503 DEPENDENCY_UNAVAILABLE）。
 *
 * <p>credit-position 只暴露额度视图，本期消费/还款累计需读账户详情（GET /api/v1/accounts/{id}）。
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
                            throw accountNotFound(accountId);
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP " + status);
                        }
                        return read(response.getBody(), AccountPositionBody.class).toPosition();
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    public AccountCycleTotals cycleTotals(String accountId) {
        try {
            return restClient.get()
                    .uri("/api/v1/accounts/{accountId}", accountId)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            throw accountNotFound(accountId);
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP " + status);
                        }
                        return read(response.getBody(), AccountBody.class).toCycleTotals();
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    public AccountProductSnapshot findProduct(String productCode) {
        try {
            return restClient.get()
                    .uri("/api/v1/account-products/{productCode}", productCode)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            throw new NotFoundException("PRODUCT_NOT_FOUND",
                                    "Account product " + productCode + " not found");
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP " + status);
                        }
                        return read(response.getBody(), AccountProductBody.class).toSnapshot();
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    /**
     * 账单全额支付：POST /api/v1/accounts/{accountId}/payments {"amount":...}。
     */
    public void payBill(String accountId, BigDecimal amount) {
        try {
            restClient.post()
                    .uri("/api/v1/accounts/{accountId}/payments", accountId)
                    .body(new AmountBody(amount.setScale(2, RoundingMode.HALF_UP)))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            throw accountNotFound(accountId);
                        }
                        if (status == 409) {
                            ApiErrorBody error = readError(response.getBody());
                            throw new BusinessRuleException(
                                    error == null || error.code() == null
                                            ? "BUSINESS_RULE_VIOLATION" : error.code(),
                                    error == null || error.message() == null
                                            ? "Account bill payment was rejected" : error.message());
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(SERVICE + " responded with HTTP " + status);
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

    private ApiErrorBody readError(InputStream body) {
        try {
            return objectMapper.readValue(body, ApiErrorBody.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private static NotFoundException accountNotFound(String accountId) {
        return new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + accountId + " not found");
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    record AccountPositionBody(String accountId, String productCode, BigDecimal creditLimit,
                              BigDecimal currentBalance, BigDecimal availableCredit) {

        AccountPosition toPosition() {
            return new AccountPosition(accountId, productCode, amount(creditLimit), amount(currentBalance),
                    amount(availableCredit));
        }
    }

    record AccountBody(String accountId, String productCode, BigDecimal currentCycleCredit,
                       BigDecimal currentCycleDebit) {

        AccountCycleTotals toCycleTotals() {
            return new AccountCycleTotals(accountId, productCode, amount(currentCycleCredit),
                    amount(currentCycleDebit));
        }
    }

    record AccountProductBody(String productCode, BigDecimal apr) {

        AccountProductSnapshot toSnapshot() {
            // APR 为月度计息的乘数（如 0.1899），不能按金额口径保留 2 位
            return new AccountProductSnapshot(productCode, apr == null ? ZERO : apr);
        }
    }

    record AmountBody(BigDecimal amount) {
    }

    record ApiErrorBody(String code, String message) {
    }
}
