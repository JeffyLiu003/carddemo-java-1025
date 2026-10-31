package com.carddemo.reconciliation.infrastructure.client;

import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * transaction-service 出站适配器：分页拉取、按业务日期过滤、下游 5xx 映射为 503 语义。
 */
class TransactionClientTest {

    private static final String BASE_URL = "http://transaction.test";
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 26);

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TransactionClient client = new TransactionClient(restClientBuilder.build());

    private static String batchJson(String batchId, String createdAt) {
        return """
                {"batchId":"%s","idempotencyKey":"idem-%s","typeCode":"CARD_PURCHASE",
                 "typeName":"Credit card purchase","accountId":"00000000006","amount":25.00,"currency":"USD",
                 "description":"Card purchase","status":"POSTED","ledgerEntryId":"JE-1",
                 "createdAt":"%s","legs":[
                   {"legNo":1,"accountRole":"CARD_RECEIVABLE","direction":"DR",
                    "ledgerAccountCode":"1100","amount":25.00},
                   {"legNo":2,"accountRole":"MERCHANT_PAYABLE","direction":"CR",
                    "ledgerAccountCode":"2100","amount":25.00}]}
                """.formatted(batchId, batchId, createdAt);
    }

    private static String pageJson(long total, int page, int size, int firstIndex, String createdAt) {
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(batchJson("b-" + (firstIndex + i), createdAt));
        }
        json.append("],\"page\":").append(page).append(",\"size\":500,\"total\":").append(total).append('}');
        return json.toString();
    }

    @Test
    void keepsFetchingPagesUntilTotalReached() {
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches?page=0&size=500"))
                .andRespond(withSuccess(pageJson(1200, 0, 500, 0, "2026-10-26T04:00:00"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches?page=1&size=500"))
                .andRespond(withSuccess(pageJson(1200, 1, 500, 500, "2026-10-26T04:00:00"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches?page=2&size=500"))
                .andRespond(withSuccess(pageJson(1200, 2, 200, 1000, "2026-10-26T04:00:00"),
                        MediaType.APPLICATION_JSON));

        List<TransactionClient.PostingBatch> batches = client.findBatches(BUSINESS_DATE);

        assertThat(batches).hasSize(1200);
        assertThat(batches.get(0).batchId()).isEqualTo("b-0");
        assertThat(batches.get(1199).batchId()).isEqualTo("b-1199");
        server.verify();
    }

    @Test
    void filtersByCreatedDateAndParsesLegs() {
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches?page=0&size=500"))
                .andRespond(withSuccess("{\"items\":[" + batchJson("b-today", "2026-10-26T04:00:00") + ","
                                + batchJson("b-yesterday", "2026-10-25T23:59:59")
                                + "],\"page\":0,\"size\":500,\"total\":2}",
                        MediaType.APPLICATION_JSON));

        List<TransactionClient.PostingBatch> batches = client.findBatches(BUSINESS_DATE);

        assertThat(batches).hasSize(1);
        TransactionClient.PostingBatch batch = batches.get(0);
        assertThat(batch.batchId()).isEqualTo("b-today");
        assertThat(batch.amount()).isEqualByComparingTo("25.00");
        assertThat(batch.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 4, 0));
        assertThat(batch.legs()).extracting(TransactionClient.PostingLeg::direction)
                .containsExactly("DR", "CR");
        assertThat(batch.legs()).extracting(TransactionClient.PostingLeg::amount)
                .containsExactly(new BigDecimal("25.00"), new BigDecimal("25.00"));
        server.verify();
    }

    @Test
    void serverErrorBecomesRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches?page=0&size=500"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findBatches(BUSINESS_DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("503");
    }
}
