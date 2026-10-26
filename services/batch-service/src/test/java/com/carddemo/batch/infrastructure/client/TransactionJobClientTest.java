package com.carddemo.batch.infrastructure.client;

import com.carddemo.batch.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * transaction-service 计息出站适配器：请求体、摘要解析、非 2xx 与 I/O 异常映射为 503 语义。
 */
class TransactionJobClientTest {

    private static final String BASE_URL = "http://transaction.test";
    private static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 10, 26);

    // 脱离 Spring Boot 自动配置直接用 RestClient 时，需自行关闭日期的时间戳序列化，否则 LocalDate 会写成数组
    private final RestClient.Builder restClientBuilder = RestClient.builder()
            .baseUrl(BASE_URL)
            .messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(
                    Jackson2ObjectMapperBuilder.json()
                            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                            .build())));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TransactionJobClient client = new TransactionJobClient(restClientBuilder.build());

    @Test
    void postsStatementDateAndSummarisesResponse() {
        server.expect(requestTo(BASE_URL + "/api/v1/interest-runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"statementDate":"2026-10-26"}
                        """))
                .andRespond(withSuccess("""
                        {"statementDate":"2026-10-26","accountsProcessed":2,"interestPosted":2,
                         "totalInterest":45.67,"details":[]}
                        """, MediaType.APPLICATION_JSON));

        String summary = client.runInterest(STATEMENT_DATE);

        assertThat(summary).isEqualTo(
                "Interest run for 2026-10-26 posted for 2 accounts, interest booked 2, total 45.67");
        server.verify();
    }

    @Test
    void ignoresUnknownFieldsAndToleratesMissingNumbers() {
        server.expect(requestTo(BASE_URL + "/api/v1/interest-runs"))
                .andRespond(withSuccess("""
                        {"statementDate":"2026-10-26","accountsProcessed":1,"unexpected":"ignored"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.runInterest(STATEMENT_DATE))
                .contains("posted for 1 accounts")
                .contains("total 0.00");
        server.verify();
    }

    @Test
    void toleratesEmptyResponseBody() {
        server.expect(requestTo(BASE_URL + "/api/v1/interest-runs"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(client.runInterest(STATEMENT_DATE)).isEqualTo("Interest run for 2026-10-26 completed");
        server.verify();
    }

    @Test
    void serverErrorBecomesRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/interest-runs"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.runInterest(STATEMENT_DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("transaction-service")
                .hasMessageContaining("503");
    }

    @Test
    void ioFailureBecomesRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/interest-runs"))
                .andRespond(request -> {
                    throw new java.io.IOException("Read timed out");
                });

        assertThatThrownBy(() -> client.runInterest(STATEMENT_DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("transaction-service is unavailable");
    }
}
