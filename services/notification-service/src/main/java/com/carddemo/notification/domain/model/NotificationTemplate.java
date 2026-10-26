package com.carddemo.notification.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Objects;

/**
 * 通知模板：subject/body 中用 {{name}} 占位，创建通知时用 payload 渲染。
 */
@Entity
@Table(name = "notification_template")
public class NotificationTemplate {

    @Id
    @Column(name = "template_code", length = 40, nullable = false, updatable = false)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false)
    private Channel channel;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "body", length = 2000, nullable = false)
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected NotificationTemplate() {
        // for JPA
    }

    private NotificationTemplate(String templateCode, Channel channel, String subject, String body) {
        this.templateCode = Objects.requireNonNull(templateCode, "templateCode must not be null");
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.subject = subject;
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.active = true;
    }

    public static NotificationTemplate draft(String templateCode, Channel channel, String subject, String body) {
        return new NotificationTemplate(templateCode, channel, subject, body);
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
