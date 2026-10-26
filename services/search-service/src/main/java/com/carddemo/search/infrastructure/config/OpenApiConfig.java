package com.carddemo.search.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI searchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Search Service")
                .version("1.0.0")
                .description("统一搜索：客户按客户号/姓名包含过滤，交易按 11 位账户号或 16 位卡号精确查询。"
                        + "下游失败时对应类型为空并记录 degraded，查询类型明确且该下游失败时返回 503。"));
    }
}
