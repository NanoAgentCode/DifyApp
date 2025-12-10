package com.github.app.dify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.domain.VectorDatabase;
import com.github.app.dify.repository.VectorDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * Elasticsearch配置类
 * 优先从数据库读取配置，如果数据库没有配置则使用application.yml的配置
 */
@Configuration
@ConfigurationProperties(prefix = "elasticsearch")
@DependsOn("vectorDatabaseRepository")
public class ElasticsearchConfig implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    // 默认值（从application.yml读取，如果数据库没有配置则使用这些值）
    private String url = "http://localhost:9200";
    private String apiKey;
    private String username;
    private String password;
    private int timeout = 30000;
    
    // 实际使用的配置值（从数据库读取或使用默认值）
    private String actualUrl;
    private String actualApiKey;
    private String actualUsername;
    private String actualPassword;
    private int actualTimeout;
    
    // Setter方法（Spring Boot需要从application.yml读取）
    public void setUrl(String url) {
        this.url = url;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
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
            logger.debug("VectorDatabaseRepository未注入，使用application.yml配置");
            actualUrl = url;
            actualApiKey = apiKey;
            actualUsername = username;
            actualPassword = password;
            actualTimeout = timeout;
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
            
            // 数据库没有配置，使用application.yml的默认值
            actualUrl = url;
            actualApiKey = apiKey;
            actualUsername = username;
            actualPassword = password;
            actualTimeout = timeout;
            logger.info("数据库中没有Elasticsearch配置，使用application.yml配置 - URL: {}", actualUrl);
        } catch (Exception e) {
            logger.warn("从数据库加载Elasticsearch配置失败，使用application.yml配置: {}", e.getMessage(), e);
            actualUrl = url;
            actualApiKey = apiKey;
            actualUsername = username;
            actualPassword = password;
            actualTimeout = timeout;
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
        return actualUrl != null ? actualUrl : url;
    }
    
    /**
     * 获取实际使用的API Key
     */
    public String getApiKey() {
        return actualApiKey != null ? actualApiKey : apiKey;
    }
    
    /**
     * 获取实际使用的用户名
     */
    public String getUsername() {
        return actualUsername != null ? actualUsername : username;
    }
    
    /**
     * 获取实际使用的密码
     */
    public String getPassword() {
        return actualPassword != null ? actualPassword : password;
    }
    
    /**
     * 获取实际使用的超时时间
     */
    public int getTimeout() {
        return actualTimeout > 0 ? actualTimeout : timeout;
    }
    
    @Override
    public void run(String... args) throws Exception {
        // Elasticsearch连接测试在需要时进行，不在启动时强制测试
    }
}

