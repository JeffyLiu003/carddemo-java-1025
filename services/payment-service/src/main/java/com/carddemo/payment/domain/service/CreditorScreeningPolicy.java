package com.carddemo.payment.domain.service;

import java.util.List;
import java.util.Locale;

/**
 * 支付筛查策略（设计 §2.2）：creditorName 非空且不在拒绝名单 → 通过。
 * 纯领域服务：不依赖 Spring、不做 I/O；名单后续由合规配置驱动。
 */
public class CreditorScreeningPolicy {

    private static final List<String> DENIED_MERCHANTS = List.of("ACME FRAUD LTD", "BLOCKED");

    /**
     * @param creditorName 收款人名称，允许为空
     * @return 通过筛查返回 {@code true}；名称为空或命中拒绝名单返回 {@code false}
     */
    public boolean isAcceptable(String creditorName) {
        if (creditorName == null || creditorName.isBlank()) {
            return false;
        }
        String normalized = creditorName.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        return DENIED_MERCHANTS.stream().noneMatch(normalized::contains);
    }

    /**
     * 拒绝原因，用于 REJECTED 单的 reject_reason。
     */
    public String rejectReason(String creditorName) {
        if (creditorName == null || creditorName.isBlank()) {
            return "CREDITOR_NAME_MISSING";
        }
        return "CREDITOR_BLOCKED";
    }
}
