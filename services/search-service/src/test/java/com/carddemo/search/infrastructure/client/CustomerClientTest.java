package com.carddemo.search.infrastructure.client;

import com.carddemo.search.domain.exception.RemoteServiceException;
import com.carddemo.search.domain.model.CustomerSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * customer-service 出站适配器：一次拉取 200 位客户用于本地过滤，4xx → 空，5xx → RemoteServiceException。
 */
class CustomerClientTest {

    private static final String BASE_URL = "http://customer.test";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
    private final CustomerClient client = new CustomerClient(restClientBuilder.build(), objectMapper);

    @Test
    void readsCustomerPage() {
        server.expect(requestTo(BASE_URL + "/api/v1/customers?page=0&size=200"))
                .andRespond(withSuccess("""
                        {"items":[{"customerId":"000000001","firstName":"Immanuel","middleName":"H",
                         "lastName":"Kessler","ssnMasked":"***-**-1234","version":1}],
                         "page":0,"size":200,"total":1}
                        """, MediaType.APPLICATION_JSON));

        List<CustomerSummary> customers = client.listCustomers();

        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).customerId()).isEqualTo("000000001");
        assertThat(customers.get(0).firstName()).isEqualTo("Immanuel");
        assertThat(customers.get(0).middleName()).isEqualTo("H");
        assertThat(customers.get(0).lastName()).isEqualTo("Kessler");
        assertThat(customers.get(0).ssnMasked()).isEqualTo("***-**-1234");
        server.verify();
    }

    @Test
    void returnsEmptyListOnClientError() {
        server.expect(requestTo(BASE_URL + "/api/v1/customers?page=0&size=200"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.listCustomers()).isEmpty();
    }

    @Test
    void mapsServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/customers?page=0&size=200"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.listCustomers())
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("500");
    }
}
