package com.carddemo.billing.application;

/**
 * 生成账单命令：accountId + period（YYYY-MM）。
 */
public record GenerateStatementCommand(String accountId, String period) {
}
