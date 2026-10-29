package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.application.RewardReversalListView;
import com.carddemo.marketing.application.RewardReversalService;
import com.carddemo.marketing.interfaces.rest.dto.ReverseRewardRequest;
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

@Tag(name = "退款与冲正回退", description = "退款/冲正事件的追扣受理（内部接口，transaction-service 调用）")
@RestController
@RequestMapping("/api/v1/reward-reversals")
public class RewardReversalController {

    private final RewardReversalService reversals;

    public RewardReversalController(RewardReversalService reversals) {
        this.reversals = reversals;
    }

    @Operation(summary = "受理退款或冲正",
            description = "按原消费交易号找到奖励记录并按活动追扣口径计算追扣金额；口径未确认时记录 PENDING 并返回 409。"
                    + "本次不移动资金，追扣执行待口径与借记能力确认。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "全额退款",
            value = """
                    {"reversalTransactionId":"0000000000000300","originalTransactionId":"0000000000000200",
                     "reversalType":"REFUND","reversalAmount":1000.00}
                    """)))
    @PostMapping
    public ResponseEntity<RewardReversalListView> accept(@Valid @RequestBody ReverseRewardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reversals.accept(request.toCommand()));
    }
}
