package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.application.EnrollmentService;
import com.carddemo.marketing.interfaces.rest.dto.EligibilityListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "活动资格", description = "客户可见的进行中活动、资格与报名状态")
@RestController
@RequestMapping("/api/v1/eligibilities")
public class EligibilityController {

    private final EnrollmentService enrollments;

    public EligibilityController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @Operation(summary = "活动与资格",
            description = "返回全部 ACTIVE 活动：eligible 由 accountId 对应账户的产品等级与账户状态判定；"
                    + "enrolled 按 accountId、customerId 或 cardNumber 任一维度判定。"
                    + "不传 accountId 时只浏览活动（eligible=false）。")
    @GetMapping
    public EligibilityListResponse eligibilities(@RequestParam(required = false) String accountId,
                                                 @RequestParam(required = false) String cardNumber,
                                                 @RequestParam(required = false) String customerId) {
        return EligibilityListResponse.from(enrollments.eligibilities(accountId, cardNumber, customerId));
    }
}
