package com.carddemo.marketing.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI marketingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CardDemo Marketing Service")
                .version("1.0.0")
                .description("营销：高端卡期限活动（1–24 个月）、报名、消费返现评估与实时贷记入账、活动效果看板。"
                        + "返现 = min(消费×费率, 单笔上限, 客户剩余额度, 活动剩余预算)，同一活动+交易只返现一次；"
                        + "贷记失败保留 PENDING 可重试。"));
    }
}
