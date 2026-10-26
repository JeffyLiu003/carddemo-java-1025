package com.carddemo.batch.domain.model;

/**
 * 作业运行状态：落库为 RUNNING，执行结束转 COMPLETED 或 FAILED。
 */
public enum RunStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
