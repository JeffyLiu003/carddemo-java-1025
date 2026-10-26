package com.carddemo.loan.application;

import java.math.BigDecimal;

/**
 * 提交贷款申请的入参（接口层 DTO 转换后传入，避免应用层依赖 Web 模型）。
 */
public record CreateLoanApplicationCommand(String customerId, String productCode, BigDecimal principal,
                                           Integer termMonths, BigDecimal annualRate) {
}
