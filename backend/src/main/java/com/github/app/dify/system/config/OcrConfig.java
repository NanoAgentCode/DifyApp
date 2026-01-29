package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OCR 服务配置（EasyOCR 等）
 */
@Component
public class OcrConfig {

    @Value("${ocr.service.url:http://localhost:8000}")
    private String serviceUrl;

    @Value("${ocr.service.timeout:30000}")
    private int timeout;

    public String getServiceUrl() {
        return serviceUrl;
    }

    public int getTimeout() {
        return timeout;
    }
}
