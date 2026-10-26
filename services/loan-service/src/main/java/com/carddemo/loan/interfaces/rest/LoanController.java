package com.carddemo.loan.interfaces.rest;

import com.carddemo.loan.application.LoanService;
import com.carddemo.loan.domain.model.RepaymentSchedule;
import com.carddemo.loan.interfaces.rest.dto.LoanResponse;
import com.carddemo.loan.interfaces.rest.dto.RepaymentRequest;
import com.carddemo.loan.interfaces.rest.dto.RepaymentResponse;
import com.carddemo.loan.interfaces.rest.dto.RepaymentScheduleListResponse;
import com.carddemo.loan.interfaces.rest.dto.RepaymentScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "贷款", description = "放款、详情、还款计划与还款（设计 14 §2.2）")
@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @Operation(summary = "放款", description = "仅 APPROVED 的申请可放款，成功置申请 DISBURSED 并记录放款时间。")
    @PostMapping("/{loanId}/disburse")
    public LoanResponse disburse(@PathVariable String loanId) {
        return LoanResponse.from(loanService.disburse(loanId));
    }

    @GetMapping("/{loanId}")
    public LoanResponse get(@PathVariable String loanId) {
        return LoanResponse.from(loanService.get(loanId));
    }

    @GetMapping("/{loanId}/schedule")
    public RepaymentScheduleListResponse schedule(@PathVariable String loanId) {
        List<RepaymentSchedule> schedule = loanService.schedule(loanId);
        return new RepaymentScheduleListResponse(loanId,
                schedule.stream().map(RepaymentScheduleResponse::from).toList());
    }

    @Operation(summary = "还款",
            description = "金额必须等于下一 PENDING 期次的 total_due 或全部剩余期次总额（提前结清），"
                    + "否则 409；逐期冲抵后本金递减，全部结清后 loan 与申请置 CLOSED。")
    @PostMapping("/{loanId}/repayments")
    public RepaymentResponse repay(@PathVariable String loanId,
                                   @Valid @RequestBody RepaymentRequest request) {
        return RepaymentResponse.from(loanService.repay(loanId, request.amount()));
    }
}
