package com.carddemo.notification.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Notification Service")
                .version("1.0.0")
                .description("通知：通知模板、{{name}} 占位渲染、通知创建与发送状态（PENDING/SENT/FAILED），"
                        + "重复发送幂等（P4）。"));
    }
}
