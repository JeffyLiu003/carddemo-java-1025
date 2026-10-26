package com.carddemo.payment.domain.exception;

/**
 * 下游服务不可用（非 2xx、超时、连接失败）。映射为 HTTP 503 DEPENDENCY_UNAVAILABLE。
 * 失败语义见 docs/design/03-service-decomposition.md §4 与设计 §2.2「下游不可用 → 503，支付单状态不变」。
 */
public class RemoteServiceException extends RuntimeException {

    public RemoteServiceException(String message) {
        super(message);
    }

    public RemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
