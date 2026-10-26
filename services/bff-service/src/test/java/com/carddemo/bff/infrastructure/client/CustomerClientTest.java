package com.carddemo.bff.infrastructure.client;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.CustomerProfile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * customer-service 出站适配器：只取掩码 SSN，4xx → 空，5xx → RemoteServiceException。
 */
class CustomerClientTest {

    private static final String BASE_URL = "http://customer.test";
    private static final String CUSTOMER_ID = "000000001";

    private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
    private final CustomerClient client = new CustomerClient(restClientBuilder.build(), objectMapper);

    @Test
    void readsMaskedCustomerProfile() {
        server.expect(requestTo(BASE_URL + "/api/v1/customers/" + CUSTOMER_ID))
                .andRespond(withSuccess("""
                        {"customerId":"000000001","firstName":"Immanuel","middleName":"H",
                         "lastName":"Kessler","ssnMasked":"***-**-1234","ficoScore":700,"version":1}
                        """, MediaType.APPLICATION_JSON));

        Optional<CustomerProfile> customer = client.findById(CUSTOMER_ID);

        assertThat(customer).isPresent();
        assertThat(customer.get().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(customer.get().firstName()).isEqualTo("Immanuel");
        assertThat(customer.get().lastName()).isEqualTo("Kessler");
        assertThat(customer.get().ssnMasked()).isEqualTo("***-**-1234");
        assertThat(customer.get().ficoScore()).isEqualTo(700);
        server.verify();
    }

    @Test
    void returnsEmptyWhenCustomerMissing() {
        server.expect(requestTo(BASE_URL + "/api/v1/customers/" + CUSTOMER_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findById(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void mapsServerErrorToRemoteServiceException() {
        server.expect(requestTo(BASE_URL + "/api/v1/customers/" + CUSTOMER_ID))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.findById(CUSTOMER_ID))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("502");
    }
}
