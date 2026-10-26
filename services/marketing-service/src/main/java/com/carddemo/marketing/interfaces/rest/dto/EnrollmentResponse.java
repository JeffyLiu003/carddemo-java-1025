package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.domain.model.Enrollment;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        String enrollmentId,
        String campaignId,
        String accountId,
        String cardNumber,
        String customerId,
        String status,
        LocalDateTime enrolledAt) {

    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getEnrollmentId(),
                enrollment.getCampaignId(),
                enrollment.getAccountId(),
                enrollment.getCardNumber(),
                enrollment.getCustomerId(),
                enrollment.getStatus().name(),
                enrollment.getEnrolledAt());
    }
}
