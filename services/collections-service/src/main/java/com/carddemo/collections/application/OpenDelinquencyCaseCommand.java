package com.carddemo.collections.application;

import java.math.BigDecimal;

/**
 * 建案命令：{"accountId":"...","daysPastDue":45,"outstandingAmount":1234.56}。
 */
public record OpenDelinquencyCaseCommand(String accountId, int daysPastDue, BigDecimal outstandingAmount) {
}
