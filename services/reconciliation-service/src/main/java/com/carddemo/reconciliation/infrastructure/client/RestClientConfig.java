package com.carddemo.reconciliation.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 下游调用客户端配置：连接 3 秒 / 读取 5 秒（application.yml 的 client.*）。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient transactionRestClient(RestClient.Builder builder,
                                            @Value("${client.transaction-service.base-url:http://localhost:8085}") String baseUrl,
                                            @Value("${client.connect-timeout:3s}") Duration connectTimeout,
                                            @Value("${client.read-timeout:5s}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    public RestClient ledgerRestClient(RestClient.Builder builder,
                                       @Value("${client.ledger-service.base-url:http://localhost:8088}") String baseUrl,
                                       @Value("${client.connect-timeout:3s}") Duration connectTimeout,
                                       @Value("${client.read-timeout:5s}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    private static RestClient build(RestClient.Builder builder, String baseUrl,
                                    Duration connectTimeout, Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
