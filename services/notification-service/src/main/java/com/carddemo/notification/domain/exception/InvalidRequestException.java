package com.carddemo.notification.domain.exception;

/**
 * 请求参数不合法（未知的状态过滤值等）。映射为 HTTP 400。
 */
public class InvalidRequestException extends RuntimeException {

    private final String code;

    public InvalidRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
