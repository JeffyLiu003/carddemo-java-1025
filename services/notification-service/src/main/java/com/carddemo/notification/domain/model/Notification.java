package com.carddemo.notification.domain.model;

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
 * 通知：创建即 PENDING 并保存渲染用的 payload，投递后转 SENT/FAILED。
 * payload 以 JSON 字符串保存（契约 §3.1），编解码由基础设施的 PayloadCodec 负责。
 */
@Entity
@Table(name = "notification")
public class Notification {

    private static final int ERROR_MAX_LENGTH = 500;

    @Id
    @Column(name = "notification_id", length = 36, nullable = false, updatable = false)
    private String notificationId;

    @Column(name = "template_code", length = 40, nullable = false, updatable = false)
    private String templateCode;

    @Column(name = "recipient", length = 200, nullable = false, updatable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false, updatable = false)
    private Channel channel;

    @Column(name = "payload", length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error", length = ERROR_MAX_LENGTH)
    private String error;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Notification() {
        // for JPA
    }

    private Notification(String notificationId, String templateCode, String recipient, Channel channel,
                         String payload, LocalDateTime createdAt) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId must not be null");
        this.templateCode = Objects.requireNonNull(templateCode, "templateCode must not be null");
        this.recipient = Objects.requireNonNull(recipient, "recipient must not be null");
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.payload = payload;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = NotificationStatus.PENDING;
    }

    public static Notification pending(String notificationId, String templateCode, String recipient,
                                       Channel channel, String payload, LocalDateTime createdAt) {
        return new Notification(notificationId, templateCode, recipient, channel, payload, createdAt);
    }

    public Notification markSent(LocalDateTime sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.error = null;
        return this;
    }

    public Notification markFailed(String error, LocalDateTime sentAt) {
        this.status = NotificationStatus.FAILED;
        this.sentAt = sentAt;
        this.error = truncate(error);
        return this;
    }

    public boolean isPending() {
        return status == NotificationStatus.PENDING;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > ERROR_MAX_LENGTH ? text.substring(0, ERROR_MAX_LENGTH) : text;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getRecipient() {
        return recipient;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getPayload() {
        return payload;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getError() {
        return error;
    }

    public long getVersion() {
        return version;
    }
}
