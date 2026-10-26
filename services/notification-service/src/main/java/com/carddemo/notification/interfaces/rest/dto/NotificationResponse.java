package com.carddemo.notification.interfaces.rest.dto;

import com.carddemo.notification.application.NotificationView;
import com.carddemo.notification.domain.model.Notification;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知详情：实体字段 + 按模板渲染后的 subject/body + 解析后的 payload。
 */
public record NotificationResponse(String notificationId,
                                   String templateCode,
                                   String recipient,
                                   String channel,
                                   String subject,
                                   String body,
                                   Map<String, String> payload,
                                   String status,
                                   LocalDateTime createdAt,
                                   LocalDateTime sentAt,
                                   String error) {

    public static NotificationResponse from(NotificationView view) {
        Notification notification = view.notification();
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getTemplateCode(),
                notification.getRecipient(),
                notification.getChannel().name(),
                view.subject(),
                view.body(),
                view.payload(),
                notification.getStatus().name(),
                notification.getCreatedAt(),
                notification.getSentAt(),
                notification.getError());
    }
}
