package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.application.RewardService;
import com.carddemo.marketing.application.RewardView;
import com.carddemo.marketing.interfaces.rest.dto.RewardListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "返现记录", description = "客户返现记录与待入账重试")
@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewards;

    public RewardController(RewardService rewards) {
        this.rewards = rewards;
    }

    @Operation(summary = "返现记录", description = "按账户号/客户号过滤，录入时间倒序分页")
    @GetMapping
    public RewardListResponse list(@RequestParam(required = false) String accountId,
                                   @RequestParam(required = false) String customerId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        return RewardListResponse.from(rewards.list(accountId, customerId, safePage, safeSize), safePage, safeSize);
    }

    @Operation(summary = "待入账重试",
            description = "PENDING/FAILED 可重试，成功置 GRANTED 并记录入账时间；已 GRANTED 重复调用幂等返回。"
                    + "贷记仍失败时保留 PENDING 与 failureReason。")
    @PostMapping("/{rewardId}/retry")
    public RewardView retry(@PathVariable String rewardId) {
        return rewards.retry(rewardId);
    }
}
