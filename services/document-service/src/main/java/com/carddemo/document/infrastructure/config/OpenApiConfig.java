package com.carddemo.document.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Document Service")
                .version("1.0.0")
                .description("文档元数据与文本内容（设计 15 §4）：上传、读取、分页、归档、删除。"));
    }
}
