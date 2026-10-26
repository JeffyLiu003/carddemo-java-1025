package com.carddemo.notification.infrastructure.persistence;

import com.carddemo.notification.domain.service.PayloadCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * payload 的 JSON 编解码：通知表只存字符串，读写时与 Map<String,String> 互转。
 */
@Component
public class JsonPayloadCodec implements PayloadCodec {

    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JsonPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String encode(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Notification payload could not be serialized", ex);
        }
    }

    @Override
    public Map<String, String> decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> decoded = objectMapper.readValue(payload, PAYLOAD_TYPE);
            return decoded == null ? Map.of() : Map.copyOf(decoded);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored notification payload is not valid JSON", ex);
        }
    }
}
