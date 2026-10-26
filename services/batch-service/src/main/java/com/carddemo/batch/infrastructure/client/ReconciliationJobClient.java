package com.carddemo.batch.infrastructure.client;

import com.carddemo.batch.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;

/**
 * reconciliation-service 出站适配器：触发当日对账并抽取下游摘要。
 */
@Component
public class ReconciliationJobClient {

    private static final String SERVICE = "reconciliation-service";
    private static final String PATH = "/api/v1/reconciliation-runs";

    private final RestClient restClient;

    public ReconciliationJobClient(@Qualifier("reconciliationRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 执行对账，返回可直接写入 job_run.message 的下游摘要；非 2xx 或超时抛 {@link RemoteServiceException}。
     */
    public String runReconciliation(LocalDate businessDate) {
        try {
            ReconciliationRunBody body = restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReconciliationRunRequest(businessDate))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RemoteServiceException(
                                SERVICE + " responded with HTTP " + response.getStatusCode().value());
                    })
                    .body(ReconciliationRunBody.class);
            if (body == null) {
                return "Reconciliation run for " + businessDate + " completed";
            }
            return "Reconciliation run " + body.runId() + " finished with status " + body.status()
                    + ", mismatches " + value(body.mismatchCount());
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    record ReconciliationRunRequest(LocalDate businessDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReconciliationRunBody(String runId,
                                 String status,
                                 Integer mismatchCount,
                                 Integer matchCount) {
    }
}
