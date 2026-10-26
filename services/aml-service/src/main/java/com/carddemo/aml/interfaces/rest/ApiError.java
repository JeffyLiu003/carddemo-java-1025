package com.carddemo.aml.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 统一错误响应。details 仅在有附加信息时输出。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<String> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}
