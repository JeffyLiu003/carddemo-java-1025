package com.carddemo.dispute.application;

import java.math.BigDecimal;

/**
 * 开案入参（接口层 DTO 转换后传入）。
 */
public record OpenDisputeCommand(String transactionId, String accountId, String customerId,
                                 String reasonCode, BigDecimal amount) {
}
