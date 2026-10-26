package com.carddemo.marketing.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 下游调用客户端配置：连接 2 秒、读取 3 秒（设计 §8：同步贷记最坏 +2s）。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient accountRestClient(RestClient.Builder builder,
                                       @Value("${client.account-service.base-url}") String baseUrl,
                                       @Value("${client.connect-timeout:2s}") Duration connectTimeout,
                                       @Value("${client.read-timeout:3s}") Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
