package com.carddemo.notification.interfaces.rest;

import com.carddemo.notification.application.CreateNotificationCommand;
import com.carddemo.notification.application.NotificationService;
import com.carddemo.notification.domain.model.Notification;
import com.carddemo.notification.domain.repository.PageResult;
import com.carddemo.notification.interfaces.rest.dto.CreateNotificationRequest;
import com.carddemo.notification.interfaces.rest.dto.NotificationListResponse;
import com.carddemo.notification.interfaces.rest.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知", description = "按模板创建通知、发送与按状态分页查询")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Operation(summary = "创建通知",
            description = "模板必须存在且启用；渲染 {{name}} 占位后立即投递，返回 SENT 或 FAILED。"
                    + "收件人包含 fail 时投递失败（便于复现 FAILED 分支）。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "还款到账通知",
            value = """
                    {"templateCode":"PAYMENT_RECEIVED","recipient":"alice@example.com",
                     "payload":{"name":"Alice","cardNumber":"4000000000000001","amount":"100.00","balance":"1900.00"}}
                    """)))
    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(NotificationResponse.from(
                notifications.create(new CreateNotificationCommand(request.templateCode(),
                        request.recipient(), request.payload()))));
    }

    @Operation(summary = "通知详情", description = "通知字段 + 渲染后的 subject/body + payload")
    @GetMapping("/{notificationId}")
    public NotificationResponse get(@PathVariable String notificationId) {
        return NotificationResponse.from(notifications.get(notificationId));
    }

    @Operation(summary = "发送通知", description = "仅 PENDING 可发送；SENT/FAILED 为终态，重复发送幂等返回当前状态。")
    @PostMapping("/{notificationId}/send")
    public NotificationResponse send(@PathVariable String notificationId) {
        return NotificationResponse.from(notifications.send(notificationId));
    }

    @Operation(summary = "通知历史", description = "可按 status（PENDING/SENT/FAILED）过滤，创建时间倒序分页")
    @GetMapping
    public NotificationListResponse list(@RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        PageResult<Notification> result = notifications.list(status, safePage, safeSize);
        return NotificationListResponse.from(result, safePage, safeSize);
    }
}
