package com.carddemo.fraud.infrastructure.config;

import com.carddemo.fraud.domain.service.FraudScoringService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * 纯领域服务，不依赖 Spring，在此装配。
     */
    @Bean
    public FraudScoringService fraudScoringService() {
        return new FraudScoringService();
    }
}
