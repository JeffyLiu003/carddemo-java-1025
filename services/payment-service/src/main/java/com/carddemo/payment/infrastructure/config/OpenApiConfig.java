package com.carddemo.payment.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Payment Service")
                .version("1.0.0")
                .description("支付指令受理、自动筛查、人工接受、清算与退汇（ACH/SEPA/WIRE/INSTANT）。"
                        + "清算以 transaction-service 的 PAYMENT 过账批次落账，退汇以冲正落账。"
                        + "演示账户：00000000001 / 00000000006。"));
    }
}
