package com.carddemo.fraud.domain.model;

/**
 * 评分决策。分值 ≥80 拒绝、≥50 复核，其余放行（docs/design/14-risk-credit-collections.md §3.2）。
 */
public enum FraudDecision {

    APPROVE,
    REVIEW,
    REJECT;

    public static final int REVIEW_THRESHOLD = 50;
    public static final int REJECT_THRESHOLD = 80;

    public static FraudDecision fromScore(int score) {
        if (score >= REJECT_THRESHOLD) {
            return REJECT;
        }
        if (score >= REVIEW_THRESHOLD) {
            return REVIEW;
        }
        return APPROVE;
    }
}
