package com.carddemo.reporting.domain.model;

/**
 * 报表运行状态：先以 RUNNING 落库，读取下游成功后转 COMPLETED，下游不可用时转 FAILED。
 */
public enum RunStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
