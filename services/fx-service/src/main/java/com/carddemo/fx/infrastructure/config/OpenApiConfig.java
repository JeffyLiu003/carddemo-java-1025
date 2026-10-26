package com.carddemo.fx.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fxOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo FX Service")
                .version("1.0.0")
                .description("外汇：汇率 upsert、5 分钟有效报价、单次消费的换汇执行与换汇记录。"
                        + "种子汇率：USD/CNY 7.10、USD/EUR 0.92、EUR/CNY 7.72。"));
    }
}
