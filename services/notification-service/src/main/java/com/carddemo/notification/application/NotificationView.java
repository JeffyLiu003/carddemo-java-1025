package com.carddemo.notification.application;

import com.carddemo.notification.domain.model.Notification;

import java.util.Map;

/**
 * 通知视图：通知实体 + 按当前模板渲染出的标题/正文 + 解析后的 payload。
 */
public record NotificationView(Notification notification, String subject, String body,
                               Map<String, String> payload) {
}
