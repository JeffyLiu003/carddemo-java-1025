package com.carddemo.fraud.domain.model;

import java.math.BigDecimal;

/**
 * 参与评分的交易画像：金额、商户名与国家。
 */
public record FraudEvaluationInput(BigDecimal amount, String merchantName, String country) {
}
