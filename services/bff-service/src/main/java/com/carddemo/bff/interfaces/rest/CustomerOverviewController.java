package com.carddemo.bff.interfaces.rest;

import com.carddemo.bff.application.CustomerOverviewService;
import com.carddemo.bff.interfaces.rest.dto.CustomerOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "客户概览", description = "客户 360 聚合：额度位置、卡片、客户、最近交易、可参与活动")
@RestController
@RequestMapping("/api/v1/bff")
@Validated
public class CustomerOverviewController {

    private final CustomerOverviewService overviews;

    public CustomerOverviewController(CustomerOverviewService overviews) {
        this.overviews = overviews;
    }

    @Operation(summary = "客户 360 概览",
            description = "账户不存在返回 404 ACCOUNT_NOT_FOUND，account-service 不可用返回 503；"
                    + "卡片/客户/交易/权益下游失败只降级并记录在 degraded。")
    @GetMapping("/customer-overview")
    public CustomerOverviewResponse customerOverview(
            @RequestParam
            @NotBlank(message = "Account id must be supplied.")
            @Pattern(regexp = "\\d{11}", message = "Account id must be 11 digits.")
            String accountId) {
        return CustomerOverviewResponse.from(overviews.overview(accountId));
    }
}
