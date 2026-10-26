package com.carddemo.notification.interfaces.rest;

import com.carddemo.notification.application.CreateTemplateCommand;
import com.carddemo.notification.application.NotificationService;
import com.carddemo.notification.domain.model.NotificationTemplate;
import com.carddemo.notification.interfaces.rest.dto.CreateTemplateRequest;
import com.carddemo.notification.interfaces.rest.dto.TemplateListResponse;
import com.carddemo.notification.interfaces.rest.dto.TemplateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知模板", description = "通知模板目录与新建（{{name}} 占位，按渠道区分 EMAIL/SMS/PUSH）")
@RestController
@RequestMapping("/api/v1/notification-templates")
public class NotificationTemplateController {

    private final NotificationService notifications;

    public NotificationTemplateController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Operation(summary = "模板列表", description = "按 templateCode 升序返回全部模板（含停用模板）")
    @GetMapping
    public TemplateListResponse list() {
        return TemplateListResponse.from(notifications.listTemplates());
    }

    @Operation(summary = "新建模板", description = "创建后即为 active；templateCode 已存在返回 409 TEMPLATE_EXISTS。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "欢迎邮件",
            value = """
                    {"templateCode":"WELCOME","channel":"EMAIL","subject":"欢迎","body":"尊敬的 {{name}}，欢迎使用。"}
                    """)))
    @PostMapping
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody CreateTemplateRequest request) {
        NotificationTemplate template = notifications.createTemplate(new CreateTemplateCommand(
                request.templateCode(), request.channel(), request.subject(), request.body()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TemplateResponse.from(template));
    }
}
