package com.carddemo.batch.infrastructure.client;

import com.carddemo.batch.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * transaction-service 出站适配器：触发计息作业并抽取下游摘要（契约 §7.2 GET /api/v1/interest-runs 之前的 POST）。
 */
@Component
public class TransactionJobClient {

    private static final String SERVICE = "transaction-service";
    private static final String PATH = "/api/v1/interest-runs";

    private final RestClient restClient;

    public TransactionJobClient(@Qualifier("transactionRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 执行计息，返回可直接写入 job_run.message 的下游摘要；非 2xx 或超时抛 {@link RemoteServiceException}。
     */
    public String runInterest(LocalDate statementDate) {
        try {
            InterestRunBody body = restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InterestRunRequest(statementDate))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RemoteServiceException(
                                SERVICE + " responded with HTTP " + response.getStatusCode().value());
                    })
                    .body(InterestRunBody.class);
            if (body == null) {
                return "Interest run for " + statementDate + " completed";
            }
            return "Interest run for " + statementDate + " posted for "
                    + value(body.accountsProcessed()) + " accounts, interest booked "
                    + value(body.interestPosted()) + ", total " + amount(body.totalInterest());
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private static String amount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.toPlainString();
    }

    record InterestRunRequest(LocalDate statementDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InterestRunBody(LocalDate statementDate,
                           Integer accountsProcessed,
                           Integer interestPosted,
                           BigDecimal totalInterest) {
    }
}
