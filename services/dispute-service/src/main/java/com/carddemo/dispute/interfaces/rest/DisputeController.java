package com.carddemo.dispute.interfaces.rest;

import com.carddemo.dispute.application.DisputeCaseService;
import com.carddemo.dispute.domain.model.DisputeCase;
import com.carddemo.dispute.domain.repository.PageResult;
import com.carddemo.dispute.interfaces.rest.dto.DisputeCaseListResponse;
import com.carddemo.dispute.interfaces.rest.dto.DisputeCaseResponse;
import com.carddemo.dispute.interfaces.rest.dto.OpenDisputeRequest;
import com.carddemo.dispute.interfaces.rest.dto.ProvisionalCreditRequest;
import com.carddemo.dispute.interfaces.rest.dto.ResolveDisputeRequest;
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

@Tag(name = "争议案件", description = "开案、审核、临时入账、chargeback 与结案（设计 14 §5.2）")
@RestController
@RequestMapping("/api/v1/disputes")
public class DisputeController {

    private final DisputeCaseService disputeCaseService;

    public DisputeController(DisputeCaseService disputeCaseService) {
        this.disputeCaseService = disputeCaseService;
    }

    @Operation(summary = "开案", description = "初始状态 OPEN；争议金额必须大于 0 且最多 2 位小数。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "未授权交易争议 250.00",
            value = """
                    {"transactionId":"txn-0001","accountId":"00000000001","customerId":"00000000001",
                     "reasonCode":"FRAUD","amount":250.00}
                    """)))
    @PostMapping
    public ResponseEntity<DisputeCaseResponse> open(@Valid @RequestBody OpenDisputeRequest request) {
        DisputeCase disputeCase = disputeCaseService.open(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(DisputeCaseResponse.from(disputeCase));
    }

    @GetMapping("/{caseId}")
    public DisputeCaseResponse get(@PathVariable String caseId) {
        return DisputeCaseResponse.from(disputeCaseService.get(caseId));
    }

    @Operation(summary = "争议案件分页查询", description = "可按 status 与 accountId 过滤，按开案时间倒序。")
    @GetMapping
    public DisputeCaseListResponse list(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String accountId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : size;
        PageResult<DisputeCase> result = disputeCaseService.list(status, accountId, safePage, safeSize);
        return DisputeCaseListResponse.from(result, safePage, safeSize);
    }

    @Operation(summary = "受理审核", description = "OPEN → UNDER_REVIEW；其他状态返回 409。")
    @PostMapping("/{caseId}/review")
    public DisputeCaseResponse review(@PathVariable String caseId) {
        return DisputeCaseResponse.from(disputeCaseService.review(caseId));
    }

    @Operation(summary = "临时入账",
            description = "UNDER_REVIEW → PROVISIONAL_CREDIT；金额必须大于 0 且不超过争议金额，否则 409。")
    @PostMapping("/{caseId}/provisional-credit")
    public DisputeCaseResponse provisionalCredit(@PathVariable String caseId,
                                                @Valid @RequestBody ProvisionalCreditRequest request) {
        return DisputeCaseResponse.from(
                disputeCaseService.grantProvisionalCredit(caseId, request.amount()));
    }

    @Operation(summary = "发起 chargeback", description = "PROVISIONAL_CREDIT → CHARGEBACK；其他状态返回 409。")
    @PostMapping("/{caseId}/chargeback")
    public DisputeCaseResponse chargeback(@PathVariable String caseId) {
        return DisputeCaseResponse.from(disputeCaseService.chargeback(caseId));
    }

    @Operation(summary = "结案",
            description = "CHARGEBACK → WON / LOST（won=true 为 WON）；再次调用 WON / LOST → CLOSED 完成归档。")
    @PostMapping("/{caseId}/resolve")
    public DisputeCaseResponse resolve(@PathVariable String caseId,
                                       @Valid @RequestBody ResolveDisputeRequest request) {
        return DisputeCaseResponse.from(disputeCaseService.resolve(caseId, request.won()));
    }
}
