package com.carddemo.reporting.infrastructure.client;

import com.carddemo.reporting.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * ledger-service 出站适配器：期间试算平衡（GET /api/v1/trial-balance?period=YYYY-MM）。
 * 逐科目取 accountCode / debitTotal / creditTotal / netAmount；非 2xx 或超时统一抛 {@link RemoteServiceException}。
 */
@Component
public class LedgerTrialBalanceClient {

    private static final String SERVICE = "ledger-service";
    private static final String PATH = "/api/v1/trial-balance";
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final RestClient restClient;

    public LedgerTrialBalanceClient(@Qualifier("ledgerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public TrialBalance trialBalance(YearMonth period) {
        try {
            TrialBalanceBody body = restClient.get()
                    .uri(uri -> uri.path(PATH)
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
            List<TrialBalanceItem> items = body.items() == null ? List.of() : body.items().stream()
                    .map(LedgerTrialBalanceClient::toItem)
                    .toList();
            return new TrialBalance(
                    body.period() == null ? period.toString() : body.period(),
                    money(body.totalDebit()),
                    money(body.totalCredit()),
                    Boolean.TRUE.equals(body.balanced()),
                    items);
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    private static TrialBalanceItem toItem(ItemBody row) {
        BigDecimal debit = money(row.debitTotal());
        BigDecimal credit = money(row.creditTotal());
        BigDecimal net = row.netAmount() == null ? debit.subtract(credit) : money(row.netAmount());
        return new TrialBalanceItem(row.accountCode(), debit, credit, net);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 期间试算平衡：逐科目借贷合计与净额。
     */
    public record TrialBalance(String period,
                               BigDecimal totalDebit,
                               BigDecimal totalCredit,
                               boolean balanced,
                               List<TrialBalanceItem> items) {
    }

    public record TrialBalanceItem(String accountCode,
                                   BigDecimal debitTotal,
                                   BigDecimal creditTotal,
                                   BigDecimal netAmount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TrialBalanceBody(String period,
                            BigDecimal totalDebit,
                            BigDecimal totalCredit,
                            Boolean balanced,
                            List<ItemBody> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemBody(String accountCode,
                    BigDecimal debitTotal,
                    BigDecimal creditTotal,
                    BigDecimal netAmount) {
    }
}
