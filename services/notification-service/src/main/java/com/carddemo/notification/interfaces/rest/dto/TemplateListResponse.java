package com.carddemo.notification.interfaces.rest.dto;

import com.carddemo.notification.domain.model.NotificationTemplate;

import java.util.List;

public record TemplateListResponse(List<TemplateResponse> items) {

    public static TemplateListResponse from(List<NotificationTemplate> templates) {
        return new TemplateListResponse(templates.stream().map(TemplateResponse::from).toList());
    }
}
