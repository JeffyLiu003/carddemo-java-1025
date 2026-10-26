package com.carddemo.notification.interfaces.rest.dto;

import com.carddemo.notification.domain.model.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新建模板请求：{"templateCode":"WELCOME","channel":"EMAIL","subject":"...","body":"Hi {{name}}"}。
 */
public record CreateTemplateRequest(
        @NotBlank(message = "Template code must be supplied.")
        @Size(max = 40, message = "Template code must not exceed 40 characters.")
        String templateCode,
        @NotNull(message = "Channel must be supplied.")
        Channel channel,
        @Size(max = 200, message = "Subject must not exceed 200 characters.")
        String subject,
        @NotBlank(message = "Body must be supplied.")
        @Size(max = 2000, message = "Body must not exceed 2000 characters.")
        String body) {
}
