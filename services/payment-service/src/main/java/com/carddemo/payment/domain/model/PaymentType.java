package com.carddemo.payment.domain.model;

import com.carddemo.payment.domain.exception.BusinessRuleException;

/**
 * 支付类型（设计 §2.1）：ACH / SEPA / WIRE / INSTANT。
 */
public enum PaymentType {

    ACH,
    SEPA,
    WIRE,
    INSTANT;

    /**
     * 解析请求中的支付类型，非法值 → INVALID_PAYMENT_TYPE（HTTP 409）。
     */
    public static PaymentType require(String code) {
        if (code != null) {
            String normalized = code.trim().toUpperCase();
            for (PaymentType type : values()) {
                if (type.name().equals(normalized)) {
                    return type;
                }
            }
        }
        throw new BusinessRuleException("INVALID_PAYMENT_TYPE",
                "Payment type must be one of ACH, SEPA, WIRE, INSTANT.");
    }
}
