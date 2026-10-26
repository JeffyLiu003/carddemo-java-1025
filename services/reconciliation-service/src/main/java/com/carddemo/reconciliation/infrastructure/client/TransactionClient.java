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
import java.util.ArrayList;
import java.util.List;

/**
 * transaction-service 出站适配器：分页读取过账批次（契约 §5）。
 * 下游列表接口不支持按业务日期过滤，这里按 createdAt 的日期部分在本地过滤。
 */
@Component
public class TransactionClient {

    private static final String SERVICE = "transaction-service";
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 40;

    private final RestClient restClient;

    public TransactionClient(@Qualifier("transactionRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 某业务日期的全部过账批次，最多翻 40 页（20000 条）防止下游分页异常时无限循环。
     */
    public List<PostingBatch> findBatches(LocalDate businessDate) {
        List<PostingBatch> result = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            PageBody body = fetchPage(page);
            List<BatchBody> rows = body.items() == null ? List.of() : body.items();
            for (BatchBody row : rows) {
                PostingBatch batch = toBatch(row);
                if (batch.createdAt() != null && businessDate.equals(batch.createdAt().toLocalDate())) {
                    result.add(batch);
                }
            }
            if (rows.isEmpty() || (long) (page + 1) * PAGE_SIZE >= body.total()) {
                break;
            }
        }
        return result;
    }

    private PageBody fetchPage(int page) {
        try {
            PageBody body = restClient.get()
                    .uri(uri -> uri.path("/api/v1/posting-batches")
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

    private static PostingBatch toBatch(BatchBody row) {
        List<PostingLeg> legs = row.legs() == null ? List.of() : row.legs().stream()
                .map(leg -> new PostingLeg(leg.direction(), money(leg.amount())))
                .toList();
        return new PostingBatch(row.batchId(), row.typeCode(), row.accountId(), money(row.amount()),
                row.currency(), row.status(), row.reversalOf(), row.createdAt(), legs);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? new BigDecimal("0.00") : amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 过账批次快照：对账只用到批次号、金额、创建时间与分录方向金额。
     */
    public record PostingBatch(String batchId,
                               String typeCode,
                               String accountId,
                               BigDecimal amount,
                               String currency,
                               String status,
                               String reversalOf,
                               LocalDateTime createdAt,
                               List<PostingLeg> legs) {
    }

    public record PostingLeg(String direction, BigDecimal amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PageBody(List<BatchBody> items, long total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchBody(String batchId,
                     String typeCode,
                     String accountId,
                     BigDecimal amount,
                     String currency,
                     String status,
                     String reversalOf,
                     LocalDateTime createdAt,
                     List<LegBody> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LegBody(String direction, BigDecimal amount) {
    }
}
