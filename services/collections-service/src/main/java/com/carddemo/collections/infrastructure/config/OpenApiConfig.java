package com.carddemo.collections.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI collectionsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Collections Service")
                .version("1.0.0")
                .description("催收：逾期案件登记与分桶（B1-B4）、承诺还款（PTP）与结案（A1.5）。"));
    }
}
