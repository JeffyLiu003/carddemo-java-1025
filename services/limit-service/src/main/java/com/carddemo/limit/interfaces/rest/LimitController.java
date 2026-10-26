package com.carddemo.limit.interfaces.rest;

import com.carddemo.limit.application.LimitApplicationService;
import com.carddemo.limit.domain.model.LimitDefinition;
import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;
import com.carddemo.limit.interfaces.rest.dto.AmountRequest;
import com.carddemo.limit.interfaces.rest.dto.CreateLimitRequest;
import com.carddemo.limit.interfaces.rest.dto.LimitCheckResponse;
import com.carddemo.limit.interfaces.rest.dto.LimitListResponse;
import com.carddemo.limit.interfaces.rest.dto.LimitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "限额", description = "客户/账户/渠道限额的维护、占用、释放与可用性检查")
@RestController
@RequestMapping("/api/v1/limits")
public class LimitController {

    private final LimitApplicationService limits;

    public LimitController(LimitApplicationService limits) {
        this.limits = limits;
    }

    @Operation(summary = "限额列表", description = "按 scopeType/scopeId 过滤，可选分页（默认 0/20）")
    @GetMapping
    public LimitListResponse list(@RequestParam(required = false) ScopeType scopeType,
                                  @RequestParam(required = false) String scopeId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return LimitListResponse.from(limits.list(scopeType, scopeId, page, size));
    }

    @Operation(summary = "可用性检查",
            description = "不存在匹配限额时返回 allowed=false、remainingAmount=0；"
                    + "启用且剩余额度不小于 amount 时 allowed=true。")
    @GetMapping("/check")
    public LimitCheckResponse check(@RequestParam ScopeType scopeType,
                                    @RequestParam String scopeId,
                                    @RequestParam LimitType limitType,
                                    @RequestParam String currency,
                                    @RequestParam BigDecimal amount) {
        return LimitCheckResponse.from(limits.check(scopeType, scopeId, limitType, currency, amount));
    }

    @GetMapping("/{limitId}")
    public LimitResponse get(@PathVariable String limitId) {
        return LimitResponse.from(limits.get(limitId));
    }

    @Operation(summary = "创建限额",
            description = "scopeType/scopeId/limitType/currency 唯一；额度必须大于零；201 返回新建定义。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "账户消费限额",
            value = """
                    {"scopeType":"ACCOUNT","scopeId":"00000000001","limitType":"PURCHASE",
                     "currency":"USD","amount":1000.00,"active":true}
                    """)))
    @PostMapping
    public ResponseEntity<LimitResponse> create(@Valid @RequestBody CreateLimitRequest request) {
        LimitDefinition definition = limits.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(LimitResponse.from(definition));
    }

    @Operation(summary = "调整额度", description = "新额度不得低于已用金额，否则 409 LIMIT_BELOW_USED")
    @PutMapping("/{limitId}")
    public LimitResponse adjust(@PathVariable String limitId,
                                @Valid @RequestBody AmountRequest request) {
        return LimitResponse.from(limits.adjust(limitId, request.amount()));
    }

    @Operation(summary = "占用额度", description = "累计已用超过额度返回 409 LIMIT_EXCEEDED")
    @PostMapping("/{limitId}/usage")
    public LimitResponse reserve(@PathVariable String limitId,
                                 @Valid @RequestBody AmountRequest request) {
        return LimitResponse.from(limits.reserve(limitId, request.amount()));
    }

    @Operation(summary = "释放额度", description = "释放金额超过已用金额返回 409 INVALID_LIMIT_RELEASE")
    @PostMapping("/{limitId}/release")
    public LimitResponse release(@PathVariable String limitId,
                                 @Valid @RequestBody AmountRequest request) {
        return LimitResponse.from(limits.release(limitId, request.amount()));
    }
}
