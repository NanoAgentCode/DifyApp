package com.github.app.dify.system.config;

import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
/**
 * Chroma配置类
 * 注意：
 * 1. 本项目使用HTTP REST API访问Chroma
 * 2. Docker部署的Chroma默认不需要API key，仅在启用认证时才需要配置
 * 3. 仅从数据库读取配置
 */
@Configuration
@DependsOn("vectorDatabaseRepository")
public class ChromaConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromaConfig.class);
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    // 实际使用的配置值（从数据库读取）
    private String actualUrl;
    private String actualApiKey;
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
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType("chroma");
            if (defaultConfig.isPresent()) {
                VectorDatabase config = defaultConfig.get();
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Chroma配置（默认） - 名称: {}, URL: {}", config.getName(), actualUrl);
                return;
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType("chroma");
            if (!enabledConfigs.isEmpty()) {
                VectorDatabase config = enabledConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Chroma配置（第一个启用） - 名称: {}, URL: {}", config.getName(), actualUrl);
                return;
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType("chroma");
            if (!allConfigs.isEmpty()) {
                VectorDatabase config = allConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Chroma配置（任意） - 名称: {}, URL: {}, 启用状态: {}", 
                        config.getName(), actualUrl, config.getEnabled());
                return;
            }
            
            logger.warn("数据库中没有Chroma配置");
        } catch (Exception e) {
            logger.error("从数据库加载Chroma配置失败: {}", e.getMessage(), e);
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
     * 获取实际使用的超时时间
     */
    public int getTimeout() {
        return actualTimeout > 0 ? actualTimeout : 30000;
    }
}