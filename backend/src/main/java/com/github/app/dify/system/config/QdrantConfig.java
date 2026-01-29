package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量库配置（可选，无配置时使用数据库中的向量库配置）
 */
@Component
public class QdrantConfig {

    @Value("${qdrant.url:http://localhost:6333}")
    private String url;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    @Value("${qdrant.timeout:30000}")
    private int timeout;

    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Integer getTimeout() {
        return timeout;
    }

    /** 重新加载配置（由 VectorDatabaseServiceImpl 在切换默认配置时调用） */
    public void reload() {
    }
}
