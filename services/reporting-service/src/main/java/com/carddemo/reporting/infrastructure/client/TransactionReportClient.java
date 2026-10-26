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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * transaction-service 出站适配器：业务日交易报表（GET /api/v1/reports/transactions）。
 * 分页读取当日全部交易，逐笔取 cardNumber 与 amount；非 2xx 或超时统一抛 {@link RemoteServiceException}。
 */
@Component
public class TransactionReportClient {

    private static final String SERVICE = "transaction-service";
    private static final String PATH = "/api/v1/reports/transactions";
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 40;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final RestClient restClient;

    public TransactionReportClient(@Qualifier("transactionRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 同一天（startDate == endDate）的全部交易，total 为下游给出的记录数。
     */
    public TransactionReport transactionsOn(LocalDate businessDate) {
        List<TransactionLine> lines = new ArrayList<>();
        long total = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            ReportBody body = fetchPage(businessDate, page);
            List<LineBody> items = body.items() == null ? List.of() : body.items();
            for (LineBody item : items) {
                lines.add(new TransactionLine(item.cardNumber(), money(item.amount())));
            }
            total = body.total() == null ? lines.size() : body.total();
            if (items.isEmpty() || lines.size() >= total) {
                break;
            }
        }
        return new TransactionReport(List.copyOf(lines), total);
    }

    private ReportBody fetchPage(LocalDate businessDate, int page) {
        try {
            ReportBody body = restClient.get()
                    .uri(uri -> uri.path(PATH)
                            .queryParam("startDate", businessDate.toString())
                            .queryParam("endDate", businessDate.toString())
                            .queryParam("page", page)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RemoteServiceException(
                                SERVICE + " responded with HTTP " + response.getStatusCode().value());
                    })
                    .body(ReportBody.class);
            if (body == null) {
                throw new RemoteServiceException(SERVICE + " returned an empty report for " + businessDate);
            }
            return body;
        } catch (RestClientException ex) {
            throw new RemoteServiceException(SERVICE + " is unavailable", ex);
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 下游交易报表：逐笔明细 + 记录数。
     */
    public record TransactionReport(List<TransactionLine> items, long total) {
    }

    public record TransactionLine(String cardNumber, BigDecimal amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReportBody(List<LineBody> items, Long total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LineBody(String cardNumber, BigDecimal amount) {
    }
}
