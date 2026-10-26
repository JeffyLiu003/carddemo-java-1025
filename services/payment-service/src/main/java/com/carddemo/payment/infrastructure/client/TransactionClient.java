package com.carddemo.payment.infrastructure.client;

import com.carddemo.payment.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * transaction-service 出站适配器：支付清算以过账批次（type PAYMENT，DECREASE）落账，退汇以冲正落账。
 * 失败语义（设计 §2.2）：非 2xx / 超时 / 连接失败 → {@link RemoteServiceException}，支付单状态不变。
 */
@Component
public class TransactionClient {

    private static final String SERVICE = "transaction-service";
    private static final String PAYMENT_TYPE_CODE = "PAYMENT";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TransactionClient(@Qualifier("transactionRestClient") RestClient restClient,
                             ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交支付过账批次，返回 transaction-service 生成的批次号。
     */
    public String postPayment(String idempotencyKey, String accountId, BigDecimal amount, String currency,
                              String description) {
        PostingBatchBody body = new PostingBatchBody(idempotencyKey, PAYMENT_TYPE_CODE, accountId, amount,
                currency, description);
        try {
            return restClient.post()
                    .uri("/api/v1/posting-batches")
                    .body(body)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(
                                    SERVICE + " responded with HTTP " + response.getStatusCode().value());
                        }
                        PostingBatchResponseBody posted =
                                objectMapper.readValue(response.getBody(), PostingBatchResponseBody.class);
                        if (posted.batchId() == null) {
                            throw new RemoteServiceException(SERVICE + " returned no posting batch id");
                        }
                        return posted.batchId();
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    /**
     * 冲正支付过账批次（退汇）。
     */
    public void reverseBatch(String batchId) {
        try {
            restClient.post()
                    .uri("/api/v1/posting-batches/{batchId}/reversal", batchId)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(
                                    SERVICE + " responded with HTTP " + response.getStatusCode().value());
                        }
                        return null;
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }
}
