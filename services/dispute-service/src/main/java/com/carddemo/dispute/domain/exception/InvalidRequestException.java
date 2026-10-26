package com.carddemo.dispute.domain.exception;

/**
 * 请求语义不成立（例如分页过滤值非法）。映射为 HTTP 400。
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
