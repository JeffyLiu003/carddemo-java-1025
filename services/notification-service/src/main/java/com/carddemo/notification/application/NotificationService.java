package com.carddemo.notification.application;

import com.carddemo.notification.domain.exception.BusinessRuleException;
import com.carddemo.notification.domain.exception.InvalidRequestException;
import com.carddemo.notification.domain.exception.NotFoundException;
import com.carddemo.notification.domain.model.Notification;
import com.carddemo.notification.domain.model.NotificationStatus;
import com.carddemo.notification.domain.model.NotificationTemplate;
import com.carddemo.notification.domain.repository.NotificationRepository;
import com.carddemo.notification.domain.repository.NotificationTemplateRepository;
import com.carddemo.notification.domain.repository.PageResult;
import com.carddemo.notification.domain.service.PayloadCodec;
import com.carddemo.notification.domain.service.TemplateRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 通知用例（P4）：模板目录、按模板渲染创建通知、发送与查询。
 *
 * <p>投递为进程内模拟（无外部网关）：收件人包含 {@code fail} 时投递失败，便于复现 FAILED 分支。
 * 创建即尝试投递（契约 §3.2），因此新建的通知直接是 SENT/FAILED；发送接口只对 PENDING 生效，
 * SENT/FAILED 为终态，重复发送幂等返回当前状态。
 */
@Service
public class NotificationService {

    private static final String FAILURE_MARKER = "fail";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationTemplateRepository templates;
    private final NotificationRepository notifications;
    private final PayloadCodec payloadCodec;

    public NotificationService(NotificationTemplateRepository templates,
                               NotificationRepository notifications,
                               PayloadCodec payloadCodec) {
        this.templates = templates;
        this.notifications = notifications;
        this.payloadCodec = payloadCodec;
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplate> listTemplates() {
        return templates.findAll();
    }

    @Transactional
    public NotificationTemplate createTemplate(CreateTemplateCommand command) {
        if (templates.findByCode(command.templateCode()).isPresent()) {
            throw new BusinessRuleException("TEMPLATE_EXISTS",
                    "Template " + command.templateCode() + " already exists");
        }
        return templates.save(NotificationTemplate.draft(command.templateCode(), command.channel(),
                command.subject(), command.body()));
    }

    /**
     * 创建通知：模板必须存在且启用；先落 PENDING，再按模板渲染并投递。
     */
    @Transactional
    public NotificationView create(CreateNotificationCommand command) {
        NotificationTemplate template = requireActiveTemplate(command.templateCode());
        Map<String, String> payload = command.payload() == null ? Map.of() : command.payload();
        Notification notification = notifications.save(Notification.pending(UUID.randomUUID().toString(),
                template.getTemplateCode(), command.recipient(), template.getChannel(),
                payloadCodec.encode(payload), LocalDateTime.now()));
        return view(deliver(notification));
    }

    /**
     * 发送：仅 PENDING 可发送；SENT/FAILED 直接返回当前状态（幂等）。
     */
    @Transactional
    public NotificationView send(String notificationId) {
        Notification notification = requireNotification(notificationId);
        return view(notification.isPending() ? deliver(notification) : notification);
    }

    @Transactional(readOnly = true)
    public NotificationView get(String notificationId) {
        return view(requireNotification(notificationId));
    }

    @Transactional(readOnly = true)
    public PageResult<Notification> list(String status, int page, int size) {
        return notifications.search(parseStatus(status), Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    private Notification deliver(Notification notification) {
        LocalDateTime now = LocalDateTime.now();
        if (notification.getRecipient().toLowerCase(Locale.ROOT).contains(FAILURE_MARKER)) {
            return notifications.save(notification.markFailed(
                    "Delivery to " + notification.getRecipient() + " failed", now));
        }
        return notifications.save(notification.markSent(now));
    }

    private NotificationView view(Notification notification) {
        Map<String, String> payload = payloadCodec.decode(notification.getPayload());
        NotificationTemplate template = templates.findByCode(notification.getTemplateCode()).orElse(null);
        if (template == null) {
            return new NotificationView(notification, null, "", payload);
        }
        return new NotificationView(notification,
                TemplateRenderer.render(template.getSubject(), payload),
                TemplateRenderer.render(template.getBody(), payload),
                payload);
    }

    private Notification requireNotification(String notificationId) {
        return notifications.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND",
                        "Notification " + notificationId + " not found"));
    }

    private NotificationTemplate requireActiveTemplate(String templateCode) {
        NotificationTemplate template = templates.findByCode(templateCode)
                .orElseThrow(() -> new NotFoundException("TEMPLATE_NOT_FOUND",
                        "Template " + templateCode + " is not defined"));
        if (!template.isActive()) {
            throw new BusinessRuleException("TEMPLATE_INACTIVE", "Template " + templateCode + " is inactive");
        }
        return template;
    }

    private static NotificationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return NotificationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_STATUS",
                    "Status filter must be one of PENDING, SENT, FAILED.");
        }
    }
}
