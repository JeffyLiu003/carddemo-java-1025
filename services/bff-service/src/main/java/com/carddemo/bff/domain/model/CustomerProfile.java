package com.carddemo.bff.domain.model;

/**
 * 客户概要。SSN 只透传掩码形式（BR-CUST-07），BFF 不做任何还原。
 */
public record CustomerProfile(
        String customerId,
        String firstName,
        String lastName,
        String ssnMasked,
        Integer ficoScore) {
}
