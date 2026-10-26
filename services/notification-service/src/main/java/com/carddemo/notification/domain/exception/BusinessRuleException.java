package com.carddemo.notification.domain.exception;

/**
 * 业务规则断言失败（模板停用、模板代码重复）。映射为 HTTP 409。
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
