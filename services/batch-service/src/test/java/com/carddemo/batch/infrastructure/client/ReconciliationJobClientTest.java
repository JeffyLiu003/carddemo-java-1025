package com.carddemo.batch.infrastructure.client;

import com.carddemo.batch.domain.exception.RemoteServiceException;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
 * reconciliation-service 对账出站适配器：201 视为成功，非 2xx/超时映射为 503 语义。
 */
class ReconciliationJobClientTest {

    private static final String BASE_URL = "http://reconciliation.test";
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 26);

    // 脱离 Spring Boot 自动配置直接用 RestClient 时，需自行关闭日期的时间戳序列化，否则 LocalDate 会写成数组
    private final RestClient.Builder restClientBuilder = RestClient.builder()
            .baseUrl(BASE_URL)
            .messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(
                    Jackson2ObjectMapperBuilder.json()
                            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                            .build())));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ReconciliationJobClient client = new ReconciliationJobClient(restClientBuilder.build());

    @Test
    void postsBusinessDateAndSummarisesResponse() {
        server.expect(requestTo(BASE_URL + "/api/v1/reconciliation-runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"businessDate":"2026-10-26"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"runId":"run-1","businessDate":"2026-10-26","status":"COMPLETED",
                                 "matched":true,"matchCount":3,"mismatchCount":0,"items":[]}
                                """));

        String summary = client.runReconciliation(BUSINESS_DATE);

        assertThat(summary).isEqualTo(
                "Reconciliation run run-1 finished with status COMPLETED, mismatches 0");
        server.verify();
    }

    @Test
    void serverErrorBecomesRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/reconciliation-runs"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.runReconciliation(BUSINESS_DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("reconciliation-service")
                .hasMessageContaining("500");
    }

    @Test
    void ioFailureBecomesRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/reconciliation-runs"))
                .andRespond(request -> {
                    throw new java.io.IOException("Connection reset");
                });

        assertThatThrownBy(() -> client.runReconciliation(BUSINESS_DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("reconciliation-service is unavailable");
    }
}
