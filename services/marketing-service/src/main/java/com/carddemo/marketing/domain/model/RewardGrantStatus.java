package com.carddemo.marketing.domain.model;

/**
 * 返现发放状态（设计 §3.1）。PENDING 表示已预占预算但尚未贷记成功，可重试（PC-10）。
 */
public enum RewardGrantStatus {
    PENDING,
    GRANTED,
    FAILED;

    public boolean isRetryable() {
        return this == PENDING || this == FAILED;
    }
}
