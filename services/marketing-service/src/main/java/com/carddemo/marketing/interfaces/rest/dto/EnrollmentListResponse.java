package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.domain.model.Enrollment;
import com.carddemo.marketing.domain.repository.PageResult;

import java.util.List;

public record EnrollmentListResponse(
        List<EnrollmentResponse> items,
        int page,
        int size,
        long total) {

    public static EnrollmentListResponse from(PageResult<Enrollment> result, int page, int size) {
        return new EnrollmentListResponse(
                result.items().stream().map(EnrollmentResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
