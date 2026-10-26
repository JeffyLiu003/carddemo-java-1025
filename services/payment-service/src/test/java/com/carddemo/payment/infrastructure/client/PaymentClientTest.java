package com.carddemo.payment.infrastructure.client;

import com.carddemo.payment.domain.exception.RemoteServiceException;
import com.carddemo.payment.domain.model.AccountSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 出站适配器的失败语义（设计 §2.2）：account 404 → 空；transaction 非 2xx → 503 依赖不可用。
 */
class PaymentClientTest {

    private static final String BASE_URL = "http://localhost:8084";

    private MockRestServiceServer server;
    private AccountClient accountClient;
    private TransactionClient transactionClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        ObjectMapper objectMapper = new ObjectMapper();
        accountClient = new AccountClient(restClient, objectMapper);
        transactionClient = new TransactionClient(restClient, objectMapper);
    }

    @Test
    void findAccountReturnsSnapshot() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/00000000006"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"accountId":"00000000006","activeStatus":"Y","currentBalance":1940.00}
                        """, MediaType.APPLICATION_JSON));

        Optional<AccountSnapshot> account = accountClient.findAccount("00000000006");

        assertThat(account).contains(new AccountSnapshot("00000000006", "Y"));
        server.verify();
    }

    @Test
    void findAccountReturnsEmptyWhenAccountDoesNotExist() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/99999999999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(accountClient.findAccount("99999999999")).isEmpty();
        server.verify();
    }

    @Test
    void findAccountMapsServerErrorToDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/accounts/00000000006"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> accountClient.findAccount("00000000006"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("account-service");
        server.verify();
    }

    @Test
    void postPaymentSendsPaymentPostingBatchAndReturnsBatchId() {
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"idempotencyKey":"payment:p1","typeCode":"PAYMENT","accountId":"00000000006",
                         "amount":120.00,"currency":"USD","description":"Utility bill"}
                        """, true))
                .andRespond(withSuccess("""
                        {"batchId":"BATCH-0001","typeCode":"PAYMENT","status":"POSTED"}
                        """, MediaType.APPLICATION_JSON));

        String batchId = transactionClient.postPayment("payment:p1", "00000000006",
                new BigDecimal("120.00"), "USD", "Utility bill");

        assertThat(batchId).isEqualTo("BATCH-0001");
        server.verify();
    }

    @Test
    void postPaymentMapsNon2xxToDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> transactionClient.postPayment("payment:p2", "00000000006",
                new BigDecimal("120.00"), "USD", "Utility bill"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("transaction-service");
        server.verify();
    }

    @Test
    void reverseBatchPostsReversalOfPostingBatch() {
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches/BATCH-0001/reversal"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"batchId":"BATCH-0002","reversalOf":"BATCH-0001"}
                        """, MediaType.APPLICATION_JSON));

        transactionClient.reverseBatch("BATCH-0001");

        server.verify();
    }

    @Test
    void reverseBatchMapsNon2xxToDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/posting-batches/BATCH-0001/reversal"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> transactionClient.reverseBatch("BATCH-0001"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("transaction-service");
        server.verify();
    }
}
