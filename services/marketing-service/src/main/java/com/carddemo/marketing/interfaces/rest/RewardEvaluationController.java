package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.application.EvaluationResult;
import com.carddemo.marketing.application.RewardService;
import com.carddemo.marketing.interfaces.rest.dto.EvaluateRewardRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "返现评估", description = "交易入账后的返现评估（内部接口，transaction-service best-effort 调用）")
@RestController
@RequestMapping("/api/v1/reward-evaluations")
public class RewardEvaluationController {

    private final RewardService rewards;

    public RewardEvaluationController(RewardService rewards) {
        this.rewards = rewards;
    }

    @Operation(summary = "评估交易返现",
            description = "只评估 ACTIVE 活动：交易日期窗口、产品等级、报名、金额达标。命中则创建 PENDING 并预占预算，"
                    + "同步贷记 account-service；贷记失败返回 PENDING（failureReason）供重试，不影响交易入账。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "白金账户 1000 元消费",
            value = """
                    {"transactionId":"0000000000000200","accountId":"00000000006",
                     "cardNumber":"3344556677889900","customerId":"000000001",
                     "amount":1000.00,"occurredAt":"2026-10-26T10:15:00"}
                    """)))
    @PostMapping
    public ResponseEntity<EvaluationResult> evaluate(@Valid @RequestBody EvaluateRewardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rewards.evaluate(request.toCommand()));
    }
}
