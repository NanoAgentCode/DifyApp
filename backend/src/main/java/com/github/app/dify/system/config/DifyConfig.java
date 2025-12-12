package com.github.app.dify.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.system.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Dify配置类
 * 从系统配置表读取配置，如果系统配置表中没有，则使用默认值
 */
@Configuration
@Component
public class DifyConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DifyConfig.class);
    
    // 默认值
    private static final String DEFAULT_BASE_URL = "http://localhost:80";
    private static final int DEFAULT_TIMEOUT = 30000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    private static final String DEFAULT_FILE_URL_PREFIX = "http://localhost:80";
    
    // 配置键
    private static final String CONFIG_KEY_BASE_URL = "dify.api.defaultBaseUrl";
    private static final String CONFIG_KEY_TIMEOUT = "dify.api.timeout";
    private static final String CONFIG_KEY_CONNECT_TIMEOUT = "dify.api.connectTimeout";
    private static final String CONFIG_KEY_FILE_URL_PREFIX = "dify.api.fileUrlPrefix";
    
    @Autowired(required = false)
    private SystemConfigService systemConfigService;
    
    private String defaultBaseUrl = DEFAULT_BASE_URL;
    private int timeout = DEFAULT_TIMEOUT;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private String fileUrlPrefix = DEFAULT_FILE_URL_PREFIX;
    
    @PostConstruct
    public void init() {
        if (systemConfigService != null) {
            loadConfigFromSystemConfig();
        } else {
            logger.warn("SystemConfigService 未注入，使用默认 Dify 配置");
        }
    }
    
    /**
     * 从系统配置表加载配置
     */
    private void loadConfigFromSystemConfig() {
        try {
            // 加载默认Base URL
            String baseUrl = systemConfigService.getConfigValue(CONFIG_KEY_BASE_URL);
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                this.defaultBaseUrl = baseUrl.trim();
                logger.info("从系统配置加载 Dify Base URL: {}", this.defaultBaseUrl);
            }
            
            // 加载超时时间
            String timeoutStr = systemConfigService.getConfigValue(CONFIG_KEY_TIMEOUT);
            if (timeoutStr != null && !timeoutStr.trim().isEmpty()) {
                try {
                    this.timeout = Integer.parseInt(timeoutStr.trim());
                    logger.info("从系统配置加载 Dify Timeout: {} 毫秒", this.timeout);
                } catch (NumberFormatException e) {
                    logger.warn("系统配置中的 Dify Timeout 格式错误: {}, 使用默认值: {}", timeoutStr, DEFAULT_TIMEOUT);
                }
            }
            
            // 加载连接超时时间
            String connectTimeoutStr = systemConfigService.getConfigValue(CONFIG_KEY_CONNECT_TIMEOUT);
            if (connectTimeoutStr != null && !connectTimeoutStr.trim().isEmpty()) {
                try {
                    this.connectTimeout = Integer.parseInt(connectTimeoutStr.trim());
                    logger.info("从系统配置加载 Dify Connect Timeout: {} 毫秒", this.connectTimeout);
                } catch (NumberFormatException e) {
                    logger.warn("系统配置中的 Dify Connect Timeout 格式错误: {}, 使用默认值: {}", connectTimeoutStr, DEFAULT_CONNECT_TIMEOUT);
                }
            }
            
            // 加载文件URL前缀
            String fileUrlPrefix = systemConfigService.getConfigValue(CONFIG_KEY_FILE_URL_PREFIX);
            if (fileUrlPrefix != null && !fileUrlPrefix.trim().isEmpty()) {
                this.fileUrlPrefix = fileUrlPrefix.trim();
                logger.info("从系统配置加载 Dify File URL Prefix: {}", this.fileUrlPrefix);
            }
        } catch (Exception e) {
            logger.error("从系统配置加载 Dify 配置失败，使用默认值", e);
        }
    }
    
    /**
     * 重新加载配置（用于配置更新后刷新）
     */
    public void reload() {
        if (systemConfigService != null) {
            loadConfigFromSystemConfig();
        }
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    
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

