package com.carddemo.reconciliation.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reconciliationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Reconciliation Service")
                .version("1.0.0")
                .description("对账与 EOD：按业务日期匹配过账批次与总账日记账、期间试算平衡、"
                        + "差异登记处理与 EOD 运行记录（A1.4）。"));
    }
}
