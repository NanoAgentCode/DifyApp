package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chroma 向量库配置（可选，无配置时使用数据库中的向量库配置）
 */
@Component
public class ChromaConfig {

    @Value("${chroma.url:http://localhost:8000}")
    private String url;

    @Value("${chroma.api-key:}")
    private String apiKey;

    @Value("${chroma.timeout:30000}")
    private int timeout;

    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getTimeout() {
        return timeout;
    }
}
