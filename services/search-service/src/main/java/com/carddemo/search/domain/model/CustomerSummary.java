package com.carddemo.search.domain.model;

/**
 * customer-service 列表投影（只保留搜索与展示需要的字段）。
 */
public record CustomerSummary(
        String customerId,
        String firstName,
        String middleName,
        String lastName,
        String ssnMasked) {
}
