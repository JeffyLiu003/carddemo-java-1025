package com.carddemo.search.domain.exception;

/**
 * 下游服务不可用（5xx、超时、连接失败）。查询类型明确时映射为 HTTP 503 DEPENDENCY_UNAVAILABLE；
 * 类型为 ALL 时另一个下游仍然可以单独支撑结果，由应用服务决定是否降级。
 */
public class RemoteServiceException extends RuntimeException {

    public RemoteServiceException(String message) {
        super(message);
    }

    public RemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
