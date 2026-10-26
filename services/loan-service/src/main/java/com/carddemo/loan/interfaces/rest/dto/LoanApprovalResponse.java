package com.carddemo.loan.interfaces.rest.dto;

import com.carddemo.loan.application.LoanApproval;

import java.util.List;

/**
 * 审批通过响应：申请状态、生成的贷款与还款计划。
 */
public record LoanApprovalResponse(LoanApplicationResponse application, LoanResponse loan,
                                   List<RepaymentScheduleResponse> schedule) {

    public static LoanApprovalResponse from(LoanApproval approval) {
        return new LoanApprovalResponse(
                LoanApplicationResponse.from(approval.application()),
                LoanResponse.from(approval.loan()),
                approval.schedule().stream().map(RepaymentScheduleResponse::from).toList());
    }
}
