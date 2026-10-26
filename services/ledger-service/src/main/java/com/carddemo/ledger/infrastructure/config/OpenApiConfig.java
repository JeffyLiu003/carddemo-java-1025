package com.carddemo.ledger.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Ledger Service")
                .version("1.0.0")
                .description("总账：科目表、日记账（复式记账与冲正）、试算平衡、期间关账。"
                        + "种子期间：2026-09 CLOSED、2026-10 OPEN、2026-11 OPEN。"));
    }
}
