package com.carddemo.collections.domain.repository;

import com.carddemo.collections.domain.model.CaseStatus;
import com.carddemo.collections.domain.model.DelinquencyBucket;

/**
 * 案件列表过滤条件；null 表示不限制。
 */
public record DelinquencyCaseFilter(CaseStatus status, DelinquencyBucket bucket) {
}
