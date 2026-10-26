package com.carddemo.notification.interfaces.rest.dto;

import com.carddemo.notification.domain.model.Notification;
import com.carddemo.notification.domain.repository.PageResult;

import java.util.List;

public record NotificationListResponse(List<NotificationSummaryResponse> items,
                                       int page,
                                       int size,
                                       long total) {

    public static NotificationListResponse from(PageResult<Notification> result, int page, int size) {
        return new NotificationListResponse(
                result.items().stream().map(NotificationSummaryResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
