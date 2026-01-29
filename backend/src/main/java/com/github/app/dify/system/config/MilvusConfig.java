package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Milvus 向量库配置（可选，无配置时使用数据库中的向量库配置）
 */
@Component
public class MilvusConfig {

    @Value("${milvus.url:}")
    private String url;

    @Value("${milvus.api-key:}")
    private String apiKey;

    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    /** 重新加载配置（由 VectorDatabaseServiceImpl 在切换默认配置时调用，可空实现） */
    public void reload() {
    }
}
