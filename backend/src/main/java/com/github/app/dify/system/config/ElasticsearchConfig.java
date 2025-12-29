package com.github.app.dify.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * Elasticsearch配置类
 * 仅从数据库读取配置
 */
@Configuration
@DependsOn("vectorDatabaseRepository")
public class ElasticsearchConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    // 实际使用的配置值（从数据库读取）
    private String actualUrl;
    private String actualApiKey;
    private String actualUsername;
    private String actualPassword;
    private int actualTimeout;
    
    /**
     * 初始化配置（从数据库读取或使用默认值）
     */
    @PostConstruct
    public void init() {
        loadConfigFromDatabase();
    }
    
    /**
     * 从数据库加载配置
     */
    private void loadConfigFromDatabase() {
        if (vectorDatabaseRepository == null) {
            logger.debug("VectorDatabaseRepository未注入，无法加载配置");
            return;
        }
        
        try {
            // 先尝试查找默认的启用配置
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType("elasticsearch");
            if (defaultConfig.isPresent()) {
                VectorDatabase config = defaultConfig.get();
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                // 从extraConfig中解析username和password（如果存在）
                parseExtraConfig(config.getExtraConfig());
                logger.info("从数据库加载Elasticsearch配置（默认） - 名称: {}, URL: {}", config.getName(), actualUrl);
                return;
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType("elasticsearch");
            if (!enabledConfigs.isEmpty()) {
                VectorDatabase config = enabledConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                parseExtraConfig(config.getExtraConfig());
                logger.info("从数据库加载Elasticsearch配置（第一个启用） - 名称: {}, URL: {}", config.getName(), actualUrl);
                return;
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType("elasticsearch");
            if (!allConfigs.isEmpty()) {
                VectorDatabase config = allConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                parseExtraConfig(config.getExtraConfig());
                logger.info("从数据库加载Elasticsearch配置（任意） - 名称: {}, URL: {}, 启用状态: {}", 
                        config.getName(), actualUrl, config.getEnabled());
                return;
            }
            
            logger.warn("数据库中没有Elasticsearch配置");
        } catch (Exception e) {
            logger.error("从数据库加载Elasticsearch配置失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 解析额外配置（JSON格式，可能包含username和password）
     */
    @SuppressWarnings("unchecked")
    private void parseExtraConfig(String extraConfig) {
        if (extraConfig == null || extraConfig.trim().isEmpty()) {
            return;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(extraConfig, Map.class);
            if (config.containsKey("username")) {
                actualUsername = (String) config.get("username");
            }
            if (config.containsKey("password")) {
                actualPassword = (String) config.get("password");
            }
        } catch (Exception e) {
            logger.debug("解析Elasticsearch额外配置失败: {}", e.getMessage());
        }
    }
    
    /**
     * 重新加载配置（当数据库配置更新时调用）
     */
    public void reload() {
        loadConfigFromDatabase();
    }
    
    /**
     * 获取实际使用的URL
     */
    public String getUrl() {
        return actualUrl;
    }
    
    /**
     * 获取实际使用的API Key
     */
    public String getApiKey() {
        return actualApiKey;
    }
    
    /**
     * 获取实际使用的用户名
     */
    public String getUsername() {
        return actualUsername;
    }
    
    /**
     * 获取实际使用的密码
     */
    public String getPassword() {
        return actualPassword;
    }
    
    /**
     * 获取实际使用的超时时间（毫秒）
     */
    public int getTimeout() {
        return actualTimeout > 0 ? actualTimeout : 30000;
    }
}

