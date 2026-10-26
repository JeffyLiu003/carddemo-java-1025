package com.carddemo.batch.domain.exception;

/**
 * 下游服务不可用（5xx、超时、连接失败）。映射为 HTTP 503 DEPENDENCY_UNAVAILABLE。
 */
public class RemoteServiceException extends RuntimeException {

    public RemoteServiceException(String message) {
        super(message);
    }

    public RemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
