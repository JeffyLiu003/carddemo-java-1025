package com.carddemo.reconciliation.infrastructure.client;

import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ledger-service 出站适配器：日记账分页与按记账日期过滤、试算平衡解析、下游 5xx 映射。
 */
class LedgerClientTest {

    private static final String BASE_URL = "http://ledger.test";
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 26);

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final LedgerClient client = new LedgerClient(restClientBuilder.build());

    private static String entryJson(String entryId, String sourceId, String postedAt) {
        return """
                {"entryId":"%s","sourceType":"POSTING_BATCH","sourceId":"%s","description":"Card purchase",
                 "postedAt":"%s","status":"POSTED","createdAt":"%s","version":0,
                 "lines":[{"lineNo":1,"accountCode":"1100","direction":"DR","amount":25.00},
                          {"lineNo":2,"accountCode":"2100","direction":"CR","amount":25.00}]}
                """.formatted(entryId, sourceId, postedAt, postedAt);
    }

    private static String pageJson(long total, int page, int size, int firstIndex, String postedAt) {
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(entryJson("JE-" + (firstIndex + i), "b-" + (firstIndex + i), postedAt));
        }
        json.append("],\"page\":").append(page).append(",\"size\":500,\"total\":").append(total).append('}');
        return json.toString();
    }

    @Test
    void keepsFetchingPagesUntilTotalReached() {
        server.expect(requestTo(BASE_URL + "/api/v1/journal-entries?page=0&size=500"))
                .andRespond(withSuccess(pageJson(600, 0, 500, 0, "2026-10-26T04:00:00"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/journal-entries?page=1&size=500"))
                .andRespond(withSuccess(pageJson(600, 1, 100, 500, "2026-10-26T04:00:00"),
                        MediaType.APPLICATION_JSON));

        List<LedgerClient.JournalEntry> journals = client.findJournals(BUSINESS_DATE);

        assertThat(journals).hasSize(600);
        assertThat(journals.get(599).sourceId()).isEqualTo("b-599");
        assertThat(journals.get(599).lines()).hasSize(2);
        server.verify();
    }

    @Test
    void filtersByPostedDate() {
        server.expect(requestTo(BASE_URL + "/api/v1/journal-entries?page=0&size=500"))
                .andRespond(withSuccess("{\"items\":[" + entryJson("JE-1", "b-1", "2026-10-26T04:00:00") + ","
                                + entryJson("JE-2", "b-2", "2026-10-25T23:59:59")
                                + "],\"page\":0,\"size\":500,\"total\":2}",
                        MediaType.APPLICATION_JSON));

        List<LedgerClient.JournalEntry> journals = client.findJournals(BUSINESS_DATE);

        assertThat(journals).hasSize(1);
        assertThat(journals.get(0).entryId()).isEqualTo("JE-1");
        assertThat(journals.get(0).sourceType()).isEqualTo("POSTING_BATCH");
        server.verify();
    }

    @Test
    void readsTrialBalanceAndIgnoresUnknownFields() {
        server.expect(requestTo(BASE_URL + "/api/v1/trial-balance?period=2026-10"))
                .andRespond(withSuccess("""
                        {"period":"2026-10","totalDebit":50.00,"totalCredit":50.00,"balanced":true,
                         "items":[{"accountCode":"1100","accountName":"Card receivable","accountType":"ASSET",
                                   "debitTotal":50.00,"creditTotal":0.00,"netAmount":50.00}]}
                        """, MediaType.APPLICATION_JSON));

        LedgerClient.TrialBalance trialBalance = client.trialBalance(YearMonth.of(2026, 10));

        assertThat(trialBalance.period()).isEqualTo("2026-10");
        assertThat(trialBalance.totalDebit()).isEqualByComparingTo("50.00");
        assertThat(trialBalance.totalCredit()).isEqualByComparingTo("50.00");
        assertThat(trialBalance.balanced()).isTrue();
        server.verify();
    }

    @Test
    void serverErrorBecomesRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/trial-balance?period=2026-10"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.trialBalance(YearMonth.of(2026, 10)))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("503");
    }
}
