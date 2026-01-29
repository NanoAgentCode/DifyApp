package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 向量库配置（可选，无配置时使用数据库中的向量库配置）
 */
@Component
public class ElasticsearchConfig {

    @Value("${elasticsearch.url:http://localhost:9200}")
    private String url;

    @Value("${elasticsearch.api-key:}")
    private String apiKey;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
