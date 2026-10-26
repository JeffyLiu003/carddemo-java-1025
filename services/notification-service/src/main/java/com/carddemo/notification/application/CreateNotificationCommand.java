package com.carddemo.notification.application;

import java.util.Map;

/**
 * 创建通知命令：payload 提供模板变量，缺变量渲染为空串。
 */
public record CreateNotificationCommand(String templateCode, String recipient, Map<String, String> payload) {
}
