package com.carddemo.notification.interfaces.rest.dto;

import com.carddemo.notification.domain.model.NotificationTemplate;

/**
 * 模板响应：subject/body 保留 {{name}} 占位原文。
 */
public record TemplateResponse(String templateCode,
                               String channel,
                               String subject,
                               String body,
                               boolean active) {

    public static TemplateResponse from(NotificationTemplate template) {
        return new TemplateResponse(
                template.getTemplateCode(),
                template.getChannel().name(),
                template.getSubject(),
                template.getBody(),
                template.isActive());
    }
}
