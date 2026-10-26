package com.carddemo.payment.domain.model;

/**
 * account-service 的账户投影（支付受理只需要账户存在且为 Y）。
 */
public record AccountSnapshot(String accountId, String activeStatus) {
}
