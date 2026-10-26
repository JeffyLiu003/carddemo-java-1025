package com.carddemo.aml.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI amlOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo AML Service")
                .version("1.0.0")
                .description("制裁/名单筛查与 AML 警报处置。完全匹配 BLOCKED、包含匹配 POTENTIAL_MATCH、"
                        + "否则 CLEAR；命中生成 OPEN 警报。种子名单：ACME FRAUD LTD（KP）、"
                        + "BLOCKED TRADING（IR）、IVAN PETROV（RU）。"));
    }
}
