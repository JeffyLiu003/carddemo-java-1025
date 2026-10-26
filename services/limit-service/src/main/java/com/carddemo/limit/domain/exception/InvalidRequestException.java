package com.carddemo.limit.domain.exception;

/**
 * 请求参数格式不合法（如币种不是 3 位字母）。映射为 HTTP 400。
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
