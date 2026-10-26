package com.carddemo.bff.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 下游调用客户端配置：连接与读取超时默认 2 秒（docs/design/16-channel-and-quality.md §2.1）。
 * 超时是 BFF 降级的触发条件之一，不能比下游自身的处理时间还长。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient accountRestClient(RestClient.Builder builder,
                                       @Value("${client.account-service.base-url}") String baseUrl,
                                       @Value("${client.connect-timeout:2s}") Duration connectTimeout,
                                       @Value("${client.read-timeout:2s}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    public RestClient cardRestClient(RestClient.Builder builder,
                                     @Value("${client.card-service.base-url}") String baseUrl,
                                     @Value("${client.connect-timeout:2s}") Duration connectTimeout,
                                     @Value("${client.read-timeout:2s}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    public RestClient customerRestClient(RestClient.Builder builder,
                                         @Value("${client.customer-service.base-url}") String baseUrl,
                                         @Value("${client.connect-timeout:2s}") Duration connectTimeout,
                                         @Value("${client.read-timeout:2s}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    public RestClient transactionRestClient(RestClient.Builder builder,
                                            @Value("${client.transaction-service.base-url}") String baseUrl,
                                            @Value("${client.connect-timeout:2s}") Duration connectTimeout,
                                            @Value("${client.read-timeout:2s}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    private static RestClient build(RestClient.Builder builder, String baseUrl,
                                    Duration connectTimeout, Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
