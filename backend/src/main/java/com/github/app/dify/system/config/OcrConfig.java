package com.github.app.dify.system.config;

import com.github.app.dify.system.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * OCR服务配置类
 * 从系统配置中读取OCR服务配置
 */
@Component
@Configuration
public class OcrConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(OcrConfig.class);
    
    // 配置键
    private static final String CONFIG_KEY_URL = "ocr.service.url";
    // 已移除：ocr.service.timeout，统一使用默认30秒
    
    // 默认值
    private static final String DEFAULT_URL = "http://localhost:8000";
    private static final int DEFAULT_TIMEOUT = 30000;  // 固定30秒，不再从系统配置读取
    
    @Autowired
    private SystemConfigService systemConfigService;
    
    private String serviceUrl;
    private int timeout;
    
    @PostConstruct
    public void init() {
        loadConfig();
    }
    
    /**
     * 从系统配置加载OCR配置
     */
    public void loadConfig() {
        // 读取服务地址
        String url = systemConfigService.getConfigValue(CONFIG_KEY_URL);
        this.serviceUrl = (url != null && !url.trim().isEmpty()) ? url.trim() : DEFAULT_URL;
        
        // 超时时间固定为30秒，不再从系统配置读取
        this.timeout = DEFAULT_TIMEOUT;
        
        logger.info("OCR配置加载完成 - URL: {}, 超时时间: {}ms (固定值)", this.serviceUrl, this.timeout);
    }
    
    /**
     * 获取OCR服务地址
     */
    public String getServiceUrl() {
        return serviceUrl;
    }
    
    /**
     * 获取OCR服务超时时间（毫秒）
     */
    public int getTimeout() {
        return timeout;
    }
    
    /**
     * 设置OCR服务地址（并保存到系统配置）
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
        // 可以在这里保存到系统配置，但通常通过SystemConfigService统一管理
    }
    
    /**
     * 设置OCR服务超时时间（并保存到系统配置）
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    /**
     * 重新加载配置（用于配置更新后刷新）
     */
    public void reload() {
        loadConfig();
        logger.info("OCR配置已重新加载 - URL: {}, 超时时间: {}ms", this.serviceUrl, this.timeout);
    }
}
