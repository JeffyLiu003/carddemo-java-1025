package com.carddemo.marketing.domain.exception;

/**
 * 请求参数或活动定义不合法（费率/上限/预算/期限越界）。映射为 HTTP 400。
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
