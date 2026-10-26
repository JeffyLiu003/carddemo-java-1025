package com.carddemo.limit.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI limitOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Limit Service")
                .version("1.0.0")
                .description("限额：客户/账户/渠道维度的消费、取现、日累计与转账限额，"
                        + "支持额度调整、占用、释放与可用性检查。"));
    }
}
