package com.carddemo.transaction.infrastructure.client;

import com.carddemo.transaction.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ledger-service 出站适配器：过账批次尽力同步为总账日记账（契约 §2.4）。
 * 失败抛 {@link RemoteServiceException}，由调用方捕获并告警，不影响批次落库。
 */
@Component
public class LedgerClient {

    private static final String SERVICE = "ledger-service";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LedgerClient(@Qualifier("ledgerRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 记一笔日记账，返回总账分录号。
     */
    public String recordJournalEntry(JournalEntryCommand command) {
        try {
            return restClient.post()
                    .uri("/api/v1/journal-entries")
                    .body(command)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RemoteServiceException(
                                    SERVICE + " responded with HTTP " + response.getStatusCode().value());
                        }
                        return objectMapper.readValue(response.getBody(), JournalEntryBody.class).entryId();
                    });
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    public record JournalEntryCommand(String sourceType,
                                      String sourceId,
                                      String description,
                                      LocalDateTime postedAt,
                                      String currency,
                                      List<JournalLineCommand> lines) {
    }

    public record JournalLineCommand(String accountCode, String direction, BigDecimal amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JournalEntryBody(String entryId) {
    }
}
