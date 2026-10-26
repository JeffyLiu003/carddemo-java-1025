package com.carddemo.batch.domain.repository;

import com.carddemo.batch.domain.model.RunStatus;

/**
 * 作业运行历史过滤条件；null 表示不限制。
 */
public record JobRunFilter(String jobCode, RunStatus status) {
}
