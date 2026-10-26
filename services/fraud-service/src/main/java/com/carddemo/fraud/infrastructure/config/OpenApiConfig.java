package com.carddemo.fraud.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fraudOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Fraud Service")
                .version("1.0.0")
                .description("交易欺诈评分、规则命中与警报处置。种子规则：金额>5000（60）、"
                        + "商户 BLOCKED MERCHANT（100）、国家 KP/IR（80）；≥80 REJECT、≥50 REVIEW。"));
    }
}
