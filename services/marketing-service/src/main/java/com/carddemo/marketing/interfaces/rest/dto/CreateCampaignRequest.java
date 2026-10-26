package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.application.CreateCampaignCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建活动请求（设计 §4 创建示例）。
 */
public record CreateCampaignRequest(
        @Schema(description = "活动编码（唯一，大写）", example = "PLATINUM_3M_CASHBACK",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Code must be supplied.")
        String code,

        @Schema(description = "活动名称", example = "白金卡 3 个月消费返现", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Name must be supplied.")
        String name,

        @Schema(description = "活动说明", example = "活动期内消费 5% 返现，单笔最高 30，累计最高 300")
        String description,

        @Schema(description = "开始日期", example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Start date must be supplied.")
        LocalDate startDate,

        @Schema(description = "持续月数（1–24），endDate = startDate + N 月 - 1 天", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Duration months must be supplied.")
        @Min(value = 1, message = "Duration months must be at least 1.")
        @Max(value = 24, message = "Duration months must be at most 24.")
        Integer durationMonths,

        @Schema(description = "圈选的产品等级", example = "[\"PLATINUM\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Eligible product codes must be supplied.")
        List<String> eligibleProductCodes,

        @Schema(description = "单笔消费门槛", example = "50.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Minimum transaction amount must be supplied.")
        BigDecimal minTransactionAmount,

        @Schema(description = "返现率（0–1）", example = "0.05", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Cashback rate must be supplied.")
        BigDecimal cashbackRate,

        @Schema(description = "单笔返现上限", example = "30.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Max cashback per transaction must be supplied.")
        BigDecimal maxCashbackPerTransaction,

        @Schema(description = "客户累计返现上限", example = "300.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Max cashback per customer must be supplied.")
        BigDecimal maxCashbackPerCustomer,

        @Schema(description = "活动总预算", example = "50000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Budget must be supplied.")
        BigDecimal budget,

        @Schema(description = "是否要求报名", example = "true")
        boolean enrollmentRequired) {

    public CreateCampaignCommand toCommand() {
        return new CreateCampaignCommand(code, name, description, startDate,
                durationMonths == null ? 0 : durationMonths, eligibleProductCodes, minTransactionAmount,
                cashbackRate, maxCashbackPerTransaction, maxCashbackPerCustomer, budget, enrollmentRequired);
    }
}
