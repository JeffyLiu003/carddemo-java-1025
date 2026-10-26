package com.carddemo.notification.domain.service;

import java.util.Map;

/**
 * payload 编解码：领域内只处理 Map<String,String>，持久化时序列化为 JSON 字符串（契约 §3.1）。
 */
public interface PayloadCodec {

    String encode(Map<String, String> payload);

    Map<String, String> decode(String payload);
}
