package com.carddemo.batch.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI batchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Batch Service")
                .version("1.0.0")
                .description("批处理：作业定义与 EOD 作业执行（计息、对账），保留每次运行记录（A1.6）。"));
    }
}
