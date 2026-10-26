package com.carddemo.payment.infrastructure.config;

import com.carddemo.payment.domain.service.CreditorScreeningPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 纯领域服务的装配。领域服务本身不依赖 Spring。
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public CreditorScreeningPolicy creditorScreeningPolicy() {
        return new CreditorScreeningPolicy();
    }
}
