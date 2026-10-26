package com.carddemo.payment.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * transaction-service 过账批次响应中支付需要的字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PostingBatchResponseBody(String batchId) {
}
