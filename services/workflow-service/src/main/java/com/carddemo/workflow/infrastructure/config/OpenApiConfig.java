package com.carddemo.workflow.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workflowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Workflow Service")
                .version("1.0.0")
                .description("流程定义、任务与审批/拒绝（设计 15 §5）：只有 PENDING 可决策，重复决策 409。"));
    }
}
