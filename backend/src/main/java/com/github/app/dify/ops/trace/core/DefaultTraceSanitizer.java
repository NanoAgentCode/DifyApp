package com.github.app.dify.ops.trace.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 默认脱敏器：限制长度并隐藏常见敏感字段。
 */
@Component
public class DefaultTraceSanitizer implements TraceSanitizer {

    private static final int MAX_LENGTH = 2000;
    private static final Pattern AUTH_PATTERN = Pattern.compile("(?i)(authorization|apiKey|api_key|password)\\s*[:=]\\s*[^,\\s\"}]+");

    private final ObjectMapper objectMapper;

    public DefaultTraceSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String summarize(Object payload) {
        if (payload == null) {
            return null;
        }
        String raw = toJson(payload);
        String masked = AUTH_PATTERN.matcher(raw).replaceAll("$1=***");
        if (masked.length() > MAX_LENGTH) {
            return masked.substring(0, MAX_LENGTH) + "...(truncated)";
        }
        return masked;
    }

    private String toJson(Object payload) {
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return String.valueOf(payload);
        }
    }
}

