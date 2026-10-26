package com.carddemo.transaction.infrastructure.client;

import com.carddemo.transaction.domain.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * marketing-service 出站适配器：入账成功后提交返现评估（docs/design/17-premium-cashback-campaign.md §4）。
 * 尽力而为语义：非 2xx 或超时抛 {@link RemoteServiceException}，由调用方告警，不影响入账。
 */
@Component
public class MarketingClient {

    private static final String SERVICE = "marketing-service";

    private final RestClient restClient;

    public MarketingClient(@Qualifier("marketingRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void evaluateRewards(RewardEvaluationCommand command) {
        try {
            restClient.post()
                    .uri("/api/v1/reward-evaluations")
                    .body(command)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    /**
     * 评估请求：交易事实 + 卡/客户身份 + 发生时间。
     */
    public record RewardEvaluationCommand(String transactionId,
                                          String accountId,
                                          String cardNumber,
                                          String customerId,
                                          BigDecimal amount,
                                          LocalDateTime occurredAt) {
    }
}
