package com.carddemo.reporting.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reportingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Reporting Service")
                .version("1.0.0")
                .description("报表：报表定义、报表运行与结果行（日交易汇总 / 试算平衡），"
                        + "下游读取失败时运行保留 FAILED 并返回 503（P4）。"));
    }
}
