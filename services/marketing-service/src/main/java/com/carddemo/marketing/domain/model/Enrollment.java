package com.carddemo.marketing.domain.model;

import com.carddemo.marketing.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 报名记录（设计 §3.1）。唯一 (campaign_id, account_id)，同一账户在同一活动只报名一次。
 *
 * <p>报名前置条件（PC-04/PC-05 的报名侧）：活动必须 ACTIVE、账户产品在圈选范围内、账户可用；
 * 校验在 {@code EnrollmentService} 用 {@link Campaign} 与 {@link AccountPosition} 完成。
 */
@Entity
@Table(name = "enrollment")
public class Enrollment {

    private static final String INVALID_ENROLLMENT = "INVALID_ENROLLMENT";

    @Id
    @Column(name = "enrollment_id", length = 36, nullable = false, updatable = false)
    private String enrollmentId;

    @Column(name = "campaign_id", length = 36, nullable = false, updatable = false)
    private String campaignId;

    @Column(name = "account_id", length = 11, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "card_number", length = 16, updatable = false)
    private String cardNumber;

    @Column(name = "customer_id", length = 9, nullable = false, updatable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private LocalDateTime enrolledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Enrollment() {
        // for JPA
    }

    public static Enrollment enroll(String enrollmentId, String campaignId, String accountId,
                                    String cardNumber, String customerId, LocalDateTime enrolledAt) {
        Enrollment enrollment = new Enrollment();
        enrollment.enrollmentId = requireText(enrollmentId, "enrollmentId");
        enrollment.campaignId = requireText(campaignId, "campaignId");
        enrollment.accountId = requireText(accountId, "accountId");
        enrollment.cardNumber = blankToNull(cardNumber);
        enrollment.customerId = requireText(customerId, "customerId");
        enrollment.status = EnrollmentStatus.ACTIVE;
        enrollment.enrolledAt = Objects.requireNonNull(enrolledAt, "enrolledAt must not be null");
        return enrollment;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(INVALID_ENROLLMENT, "Enrollment " + field + " must be supplied.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 16) {
            throw new InvalidRequestException(INVALID_ENROLLMENT,
                    "Enrollment cardNumber must not exceed 16 characters.");
        }
        return trimmed;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public long getVersion() {
        return version;
    }
}
