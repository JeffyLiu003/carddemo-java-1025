package com.carddemo.loan.interfaces.rest;

import com.carddemo.loan.application.LoanApplicationService;
import com.carddemo.loan.domain.model.LoanApplication;
import com.carddemo.loan.domain.repository.PageResult;
import com.carddemo.loan.interfaces.rest.dto.CreateLoanApplicationRequest;
import com.carddemo.loan.interfaces.rest.dto.LoanApplicationListResponse;
import com.carddemo.loan.interfaces.rest.dto.LoanApplicationResponse;
import com.carddemo.loan.interfaces.rest.dto.LoanApprovalResponse;
import com.carddemo.loan.interfaces.rest.dto.RejectLoanApplicationRequest;
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

@Tag(name = "贷款申请", description = "提交、查询、审批、拒绝（设计 14 §2.2）")
@RestController
@RequestMapping("/api/v1/loan-applications")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    public LoanApplicationController(LoanApplicationService loanApplicationService) {
        this.loanApplicationService = loanApplicationService;
    }

    @Operation(summary = "提交贷款申请",
            description = "初始状态 SUBMITTED；本金 > 0 且最多 2 位小数，期限 1–360 个月，年利率 [0, 100%)。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "12 期 12000 元 18% 年利率",
            value = """
                    {"customerId":"00000000001","productCode":"PERSONAL",
                     "principal":12000.00,"termMonths":12,"annualRate":0.1800}
                    """)))
    @PostMapping
    public ResponseEntity<LoanApplicationResponse> submit(
            @Valid @RequestBody CreateLoanApplicationRequest request) {
        LoanApplication application = loanApplicationService.submit(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(LoanApplicationResponse.from(application));
    }

    @GetMapping("/{applicationId}")
    public LoanApplicationResponse get(@PathVariable String applicationId) {
        return LoanApplicationResponse.from(loanApplicationService.get(applicationId));
    }

    @Operation(summary = "贷款申请分页查询", description = "可按 status 过滤，按提交时间倒序。")
    @GetMapping
    public LoanApplicationListResponse list(@RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : size;
        PageResult<LoanApplication> result = loanApplicationService.list(status, safePage, safeSize);
        return LoanApplicationListResponse.from(result, safePage, safeSize);
    }

    @Operation(summary = "审批通过",
            description = "SUBMITTED → APPROVED，同时建账贷款并按等额本息生成还款计划；"
                    + "首期到期日为审批日 + 1 个月，非 SUBMITTED 返回 409。")
    @PostMapping("/{applicationId}/approve")
    public LoanApprovalResponse approve(@PathVariable String applicationId) {
        return LoanApprovalResponse.from(loanApplicationService.approve(applicationId));
    }

    @Operation(summary = "拒绝申请", description = "SUBMITTED → REJECTED，reason 必填。")
    @PostMapping("/{applicationId}/reject")
    public LoanApplicationResponse reject(@PathVariable String applicationId,
                                          @Valid @RequestBody RejectLoanApplicationRequest request) {
        return LoanApplicationResponse.from(loanApplicationService.reject(applicationId, request.reason()));
    }
}
