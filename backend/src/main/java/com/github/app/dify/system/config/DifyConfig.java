package com.github.app.dify.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Dify配置类
 */
@Configuration
@ConfigurationProperties(prefix = "dify.api")
public class DifyConfig {
    
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    
    private String defaultBaseUrl = "http://localhost:80";
    private int timeout = 30000;
    private int connectTimeout = 10000;
    private String fileUrlPrefix = "http://localhost:80";
    
    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }
    
    public void setDefaultBaseUrl(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    public String getFileUrlPrefix() {
        return fileUrlPrefix;
    }
    
    public void setFileUrlPrefix(String fileUrlPrefix) {
        this.fileUrlPrefix = fileUrlPrefix;
    }
}

