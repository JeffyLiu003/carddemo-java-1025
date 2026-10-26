package com.carddemo.collections.interfaces.rest;

import com.carddemo.collections.application.DelinquencyCaseService;
import com.carddemo.collections.application.OpenDelinquencyCaseCommand;
import com.carddemo.collections.domain.model.DelinquencyCase;
import com.carddemo.collections.domain.repository.PageResult;
import com.carddemo.collections.interfaces.rest.dto.DelinquencyCaseListResponse;
import com.carddemo.collections.interfaces.rest.dto.DelinquencyCaseResponse;
import com.carddemo.collections.interfaces.rest.dto.OpenDelinquencyCaseRequest;
import com.carddemo.collections.interfaces.rest.dto.PromiseToPayRequest;
import com.carddemo.collections.interfaces.rest.dto.RecalculateRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "催收", description = "逾期案件：分桶、重算、承诺还款与结案")
@RestController
@RequestMapping("/api/v1/delinquency-cases")
public class DelinquencyCaseController {

    private final DelinquencyCaseService cases;

    public DelinquencyCaseController(DelinquencyCaseService cases) {
        this.cases = cases;
    }

    @Operation(summary = "建案", description = "按逾期天数落 B1-B4 分桶，初始状态 OPEN。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "逾期 45 天",
            value = """
                    {"accountId":"00000000006","daysPastDue":45,"outstandingAmount":1234.56}
                    """)))
    @PostMapping
    public ResponseEntity<DelinquencyCaseResponse> open(@Valid @RequestBody OpenDelinquencyCaseRequest request) {
        DelinquencyCase delinquencyCase = cases.open(new OpenDelinquencyCaseCommand(
                request.accountId(), request.daysPastDue(), request.outstandingAmount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DelinquencyCaseResponse.from(delinquencyCase));
    }

    @Operation(summary = "案件详情")
    @GetMapping("/{caseId}")
    public DelinquencyCaseResponse get(@PathVariable String caseId) {
        return DelinquencyCaseResponse.from(cases.get(caseId));
    }

    @Operation(summary = "案件分页", description = "可按 status（OPEN/PTP/RESOLVED）与 bucket（B1-B4）过滤，按创建时间倒序")
    @GetMapping
    public DelinquencyCaseListResponse list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String bucket,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        PageResult<DelinquencyCase> result = cases.list(status, bucket, safePage, safeSize);
        return DelinquencyCaseListResponse.from(result, safePage, safeSize);
    }

    @Operation(summary = "重算逾期", description = "更新逾期天数并重算分桶；承诺日过期未还款的 PTP 案件退回 OPEN。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "逾期 75 天",
            value = """
                    {"daysPastDue":75}
                    """)))
    @PostMapping("/{caseId}/recalculate")
    public DelinquencyCaseResponse recalculate(@PathVariable String caseId,
                                               @Valid @RequestBody RecalculateRequest request) {
        return DelinquencyCaseResponse.from(cases.recalculate(caseId, request.daysPastDue()));
    }

    @Operation(summary = "登记承诺还款", description = "承诺日期必须晚于当天；状态置 PTP。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "承诺 2026-11-05 还款",
            value = """
                    {"date":"2026-11-05"}
                    """)))
    @PostMapping("/{caseId}/promise-to-pay")
    public DelinquencyCaseResponse promiseToPay(@PathVariable String caseId,
                                                @Valid @RequestBody PromiseToPayRequest request) {
        return DelinquencyCaseResponse.from(cases.promiseToPay(caseId, request.date()));
    }

    @Operation(summary = "结案", description = "状态置 RESOLVED 并记录 resolvedAt；终态不可再变更。")
    @PostMapping("/{caseId}/resolve")
    public DelinquencyCaseResponse resolve(@PathVariable String caseId) {
        return DelinquencyCaseResponse.from(cases.resolve(caseId));
    }
}
