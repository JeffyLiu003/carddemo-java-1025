package com.carddemo.reporting.infrastructure.client;

import com.carddemo.reporting.domain.exception.RemoteServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ledger-service 出站适配器：期间试算平衡逐科目解析（accountCode/debitTotal/creditTotal/netAmount），
 * netAmount 缺失时按借-贷推导，5xx → 503 语义。
 */
class LedgerTrialBalanceClientTest {

    private static final String BASE_URL = "http://ledger.test";
    private static final YearMonth PERIOD = YearMonth.of(2026, 10);

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final LedgerTrialBalanceClient client = new LedgerTrialBalanceClient(restClientBuilder.build());

    @Test
    void readsTrialBalanceItemsForPeriod() {
        server.expect(requestTo(allOf(
                        containsString("/api/v1/trial-balance"),
                        containsString("period=2026-10"))))
                .andRespond(withSuccess("""
                        {"period":"2026-10","totalDebit":150.00,"totalCredit":150.00,"balanced":true,
                         "items":[
                           {"accountCode":"1100","debitTotal":150.00,"creditTotal":0.00,"netAmount":150.00},
                           {"accountCode":"2100","debitTotal":0.00,"creditTotal":150.00,"netAmount":-150.00}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        LedgerTrialBalanceClient.TrialBalance trialBalance = client.trialBalance(PERIOD);

        assertThat(trialBalance.period()).isEqualTo("2026-10");
        assertThat(trialBalance.totalDebit()).isEqualByComparingTo("150.00");
        assertThat(trialBalance.balanced()).isTrue();
        assertThat(trialBalance.items()).hasSize(2);
        assertThat(trialBalance.items().get(0).accountCode()).isEqualTo("1100");
        assertThat(trialBalance.items().get(0).debitTotal()).isEqualByComparingTo("150.00");
        assertThat(trialBalance.items().get(0).netAmount()).isEqualByComparingTo("150.00");
        assertThat(trialBalance.items().get(1).netAmount()).isEqualByComparingTo("-150.00");
        server.verify();
    }

    @Test
    void derivesNetAmountWhenDownstreamOmitsIt() {
        server.expect(requestTo(allOf(containsString("/api/v1/trial-balance"))))
                .andRespond(withSuccess("""
                        {"period":"2026-10","items":[
                           {"accountCode":"4100","debitTotal":0.00,"creditTotal":80.00}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        LedgerTrialBalanceClient.TrialBalance trialBalance = client.trialBalance(PERIOD);

        assertThat(trialBalance.items()).hasSize(1);
        assertThat(trialBalance.items().get(0).netAmount()).isEqualByComparingTo("-80.00");
    }

    @Test
    void mapsUnavailableLedgerToRemoteServiceException() {
        server.expect(requestTo(allOf(containsString("/api/v1/trial-balance"))))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.trialBalance(PERIOD))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("503");
    }
}
