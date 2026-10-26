package com.carddemo.reporting.infrastructure.client;

import com.carddemo.reporting.domain.exception.RemoteServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * transaction-service 出站适配器：当日交易报表解析（items + total）、分页续取与 5xx → 503 语义。
 */
class TransactionReportClientTest {

    private static final String BASE_URL = "http://transaction.test";
    private static final String PATH = "/api/v1/reports/transactions";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TransactionReportClient client = new TransactionReportClient(restClientBuilder.build());

    @Test
    void readsDailyTransactionsWithSameStartAndEndDate() {
        server.expect(requestTo(allOf(
                        containsString(BASE_URL + PATH),
                        containsString("startDate=2026-10-26"),
                        containsString("endDate=2026-10-26"),
                        containsString("size=500"))))
                .andRespond(withSuccess("""
                        {"items":[
                           {"transactionId":"t-1","cardNumber":"4000000000000001","amount":25.00},
                           {"transactionId":"t-2","cardNumber":"4000000000000002","amount":30.00}
                         ],
                         "page":0,"size":500,"total":2,"pageTotal":55.00,"grandTotal":55.00}
                        """, MediaType.APPLICATION_JSON));

        TransactionReportClient.TransactionReport report = client.transactionsOn(DATE);

        assertThat(report.total()).isEqualTo(2);
        assertThat(report.items()).hasSize(2);
        assertThat(report.items().get(0).cardNumber()).isEqualTo("4000000000000001");
        assertThat(report.items().get(0).amount()).isEqualByComparingTo("25.00");
        assertThat(report.items().get(1).amount()).isEqualByComparingTo("30.00");
        server.verify();
    }

    @Test
    void readsEveryPageUntilTotalIsReached() {
        server.expect(requestTo(allOf(containsString(PATH), containsString("page=0"))))
                .andRespond(withSuccess("""
                        {"items":[{"cardNumber":"4000000000000001","amount":10.00}],"total":2}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(allOf(containsString(PATH), containsString("page=1"))))
                .andRespond(withSuccess("""
                        {"items":[{"cardNumber":"4000000000000002","amount":20.00}],"total":2}
                        """, MediaType.APPLICATION_JSON));

        TransactionReportClient.TransactionReport report = client.transactionsOn(DATE);

        assertThat(report.items()).hasSize(2);
        assertThat(report.total()).isEqualTo(2);
        server.verify();
    }

    @Test
    void mapsServerErrorToRemoteServiceException() {
        server.expect(requestTo(allOf(containsString(PATH))))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.transactionsOn(DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("500");
    }

    @Test
    void mapsEmptyBodyToRemoteServiceException() {
        server.expect(requestTo(allOf(containsString(PATH))))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.transactionsOn(DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("empty report");
    }
}
