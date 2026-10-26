package com.carddemo.reconciliation.infrastructure.client;

import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * ledger-service 出站适配器：分页读取日记账、读取期间试算平衡（契约 §5）。
 * 日记账按 postedAt 的日期部分在本地过滤；下游不可用统一抛 {@link RemoteServiceException}。
 */
@Component
public class LedgerClient {

    private static final String SERVICE = "ledger-service";
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 40;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final RestClient restClient;

    public LedgerClient(@Qualifier("ledgerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 某记账日期的全部日记账（含手工日记账，由对账逻辑决定是否参与匹配）。
     */
    public List<JournalEntry> findJournals(LocalDate businessDate) {
        List<JournalEntry> result = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            PageBody body = fetchPage(page);
            List<EntryBody> rows = body.items() == null ? List.of() : body.items();
            for (EntryBody row : rows) {
                JournalEntry entry = toEntry(row);
                if (entry.postedAt() != null && businessDate.equals(entry.postedAt().toLocalDate())) {
                    result.add(entry);
                }
            }
            if (rows.isEmpty() || (long) (page + 1) * PAGE_SIZE >= body.total()) {
                break;
            }
        }
        return result;
    }

    /**
     * 期间试算平衡：Σ借方 == Σ贷方 时 balanced=true。
     */
    public TrialBalance trialBalance(YearMonth period) {
        try {
            TrialBalanceBody body = restClient.get()
                    .uri(uri -> uri.path("/api/v1/trial-balance")
                            .queryParam("period", period.toString())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RemoteServiceException(
                                SERVICE + " responded with HTTP " + response.getStatusCode().value());
                    })
                    .body(TrialBalanceBody.class);
            if (body == null) {
                throw new RemoteServiceException(SERVICE + " returned an empty trial balance for " + period);
            }
            return new TrialBalance(
                    body.period() == null ? period.toString() : body.period(),
                    money(body.totalDebit()),
                    money(body.totalCredit()),
                    Boolean.TRUE.equals(body.balanced()));
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    private PageBody fetchPage(int page) {
        try {
            PageBody body = restClient.get()
                    .uri(uri -> uri.path("/api/v1/journal-entries")
                            .queryParam("page", page)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RemoteServiceException(
                                SERVICE + " responded with HTTP " + response.getStatusCode().value());
                    })
                    .body(PageBody.class);
            return body == null ? new PageBody(List.of(), 0) : body;
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    private static JournalEntry toEntry(EntryBody row) {
        List<JournalLine> lines = row.lines() == null ? List.of() : row.lines().stream()
                .map(line -> new JournalLine(line.accountCode(), line.direction(), money(line.amount())))
                .toList();
        return new JournalEntry(row.entryId(), row.sourceType(), row.sourceId(), row.postedAt(),
                row.status(), lines);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record JournalEntry(String entryId,
                               String sourceType,
                               String sourceId,
                               LocalDateTime postedAt,
                               String status,
                               List<JournalLine> lines) {
    }

    public record JournalLine(String accountCode, String direction, BigDecimal amount) {
    }

    public record TrialBalance(String period,
                               BigDecimal totalDebit,
                               BigDecimal totalCredit,
                               boolean balanced) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PageBody(List<EntryBody> items, long total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EntryBody(String entryId,
                     String sourceType,
                     String sourceId,
                     LocalDateTime postedAt,
                     String status,
                     List<LineBody> lines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LineBody(String accountCode, String direction, BigDecimal amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TrialBalanceBody(String period,
                            BigDecimal totalDebit,
                            BigDecimal totalCredit,
                            Boolean balanced) {
    }
}
