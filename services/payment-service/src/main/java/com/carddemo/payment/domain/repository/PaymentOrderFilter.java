package com.carddemo.payment.domain.repository;

/**
 * 支付指令查询条件：状态与账户号均可选（设计 §2.2 分页）。
 */
public record PaymentOrderFilter(String status, String accountId) {
}
