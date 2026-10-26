package com.carddemo.reference.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI referenceDataOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Reference Data Service")
                .version("1.0.0")
                .description("参考数据（参考码表）：CURRENCY / COUNTRY / HOLIDAY / PRODUCT_CATEGORY。"
                        + "domain 与 code 统一大写且唯一；支持按 domain 查询（active 过滤）、"
                        + "新增（重复 409）、修改 label/sortOrder/active、物理删除。"));
    }
}
