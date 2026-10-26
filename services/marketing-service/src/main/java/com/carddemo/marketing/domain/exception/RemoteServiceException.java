package com.carddemo.marketing.domain.exception;

/**
 * 下游服务不可用（5xx、超时、连接失败）或贷记被拒绝。映射为 HTTP 503 DEPENDENCY_UNAVAILABLE。
 *
 * <p>返现评估不因该异常回滚：已创建的 PENDING 发放保留，等待重试（PC-10）。
 */
public class RemoteServiceException extends RuntimeException {

    public RemoteServiceException(String message) {
        super(message);
    }

    public RemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
