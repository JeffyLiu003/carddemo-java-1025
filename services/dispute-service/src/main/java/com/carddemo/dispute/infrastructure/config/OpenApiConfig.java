package com.carddemo.dispute.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI disputeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Dispute Service")
                .version("1.0.0")
                .description("争议案件、受理审核、临时入账、chargeback 与结案（设计 14 §5）。"));
    }
}
