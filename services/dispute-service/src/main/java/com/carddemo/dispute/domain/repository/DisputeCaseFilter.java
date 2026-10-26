package com.carddemo.dispute.domain.repository;

import com.carddemo.dispute.domain.model.DisputeStatus;

/**
 * 争议案件分页过滤条件；字段为 null 表示不过滤。
 */
public record DisputeCaseFilter(DisputeStatus status, String accountId) {
}
