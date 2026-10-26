package com.carddemo.search.domain.exception;

/**
 * 请求参数取值不合法（如搜索类型不在 ALL/CUSTOMER/TRANSACTION 内）。映射为 HTTP 400。
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
