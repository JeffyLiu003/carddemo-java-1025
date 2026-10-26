package com.carddemo.loan.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI loanOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Loan Service")
                .version("1.0.0")
                .description("贷款申请、审批、放款、等额本息还款计划与还款"
                        + "（对应遗留 TRNX/ACCT 信贷切片，设计 14 §2）。"));
    }
}
