package com.carddemo.billing.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI billingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Billing Service")
                .version("1.0.0")
                .description("账单：账单生成（同账户+期间幂等）、最低还款、利息试算、到期日与全额账单支付。"
                        + "OVERDUE 按到期日与当日只读判定，不落库。"));
    }
}
