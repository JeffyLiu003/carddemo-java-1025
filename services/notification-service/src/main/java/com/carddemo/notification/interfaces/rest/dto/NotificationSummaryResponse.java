package com.carddemo.notification.interfaces.rest.dto;

import com.carddemo.notification.domain.model.Notification;

import java.time.LocalDateTime;

/**
 * 通知历史条目：不含渲染文本（详情走 GET /api/v1/notifications/{id}）。
 */
public record NotificationSummaryResponse(String notificationId,
                                          String templateCode,
                                          String recipient,
                                          String channel,
                                          String status,
                                          LocalDateTime createdAt,
                                          LocalDateTime sentAt,
                                          String error) {

    public static NotificationSummaryResponse from(Notification notification) {
        return new NotificationSummaryResponse(
                notification.getNotificationId(),
                notification.getTemplateCode(),
                notification.getRecipient(),
                notification.getChannel().name(),
                notification.getStatus().name(),
                notification.getCreatedAt(),
                notification.getSentAt(),
                notification.getError());
    }
}
