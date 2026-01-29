package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dify API 与前端配置
 */
@Component
public class DifyConfig {

    @Value("${dify.file-url-prefix:/api/files/}")
    private String fileUrlPrefix;

    @Value("${dify.default-base-url:http://localhost:3000}")
    private String defaultBaseUrl;

    @Value("${dify.timeout:300000}")
    private long timeout;

    public String getFileUrlPrefix() {
        return fileUrlPrefix;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public long getTimeout() {
        return timeout;
    }
}
