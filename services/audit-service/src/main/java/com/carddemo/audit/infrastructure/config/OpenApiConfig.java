package com.carddemo.audit.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI auditOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Audit Service")
                .version("1.0.0")
                .description("追加式审计：写入审计事件（201）、按事件号查看详情、"
                        + "按 actor / resourceType / resourceId 组合检索（newest first）。"
                        + "审计记录只追加，不提供更新与删除端点。"));
    }
}
