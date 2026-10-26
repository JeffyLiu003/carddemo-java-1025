package com.carddemo.bff.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bffOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo BFF Service")
                .version("1.0.0")
                .description("渠道聚合：客户 360 概览。聚合 account/card/customer/transaction，"
                        + "非账户下游失败只降级（degraded 列出服务名），不影响整体 200。"));
    }
}
