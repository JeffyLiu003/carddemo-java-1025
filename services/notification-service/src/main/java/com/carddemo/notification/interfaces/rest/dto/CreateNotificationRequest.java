package com.carddemo.notification.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 创建通知请求：payload 提供模板变量（Map<String,String>），缺变量渲染为空串。
 */
public record CreateNotificationRequest(
        @NotBlank(message = "Template code must be supplied.")
        String templateCode,
        @NotBlank(message = "Recipient must be supplied.")
        @Size(max = 200, message = "Recipient must not exceed 200 characters.")
        String recipient,
        Map<String, String> payload) {
}
