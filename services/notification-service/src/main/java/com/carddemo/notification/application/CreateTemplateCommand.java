package com.carddemo.notification.application;

import com.carddemo.notification.domain.model.Channel;

/**
 * 新建模板命令：创建后即为 active。
 */
public record CreateTemplateCommand(String templateCode, Channel channel, String subject, String body) {
}
