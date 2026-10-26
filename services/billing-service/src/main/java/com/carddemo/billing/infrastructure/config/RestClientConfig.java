package com.carddemo.billing.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 下游调用客户端配置：连接超时 3 秒、读取超时 5 秒（docs/design/03-service-decomposition.md §4）。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient accountRestClient(RestClient.Builder builder,
                                       @Value("${client.account-service.base-url}") String baseUrl,
                                       @Value("${client.connect-timeout:3s}") Duration connectTimeout,
                                       @Value("${client.read-timeout:5s}") Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
