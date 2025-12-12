package com.github.app.dify.appsystemdata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.appknowledgebase.domain.VectorDatabase;
import com.github.app.dify.appknowledgebase.repository.VectorDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * Elasticsearch配置类
 * 优先从数据库读取配置，如果数据库没有配置则使用 Spring Boot 标准配置 (spring.elasticsearch.*)
 */
@Configuration
@DependsOn("vectorDatabaseRepository")
public class ElasticsearchConfig implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    // 默认值（从 Spring Boot 标准配置 spring.elasticsearch.* 读取）
    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String uris;
    
    @Value("${spring.elasticsearch.username:}")
    private String username;
    
    @Value("${spring.elasticsearch.password:}")
    private String password;
    
    @Value("${spring.elasticsearch.connection-timeout:30s}")
    private String connectionTimeout;
    
    // API Key 不在 Spring Boot 标准配置中，保留为空（如果需要，可以从数据库读取）
    private String apiKey;
    
    // 实际使用的配置值（从数据库读取或使用默认值）
    private String actualUrl;
    private String actualApiKey;
    private String actualUsername;
    private String actualPassword;
    private int actualTimeout;
    
    /**
     * 从 Spring Boot 配置的 URIs 中提取第一个 URL
     */
    private String getUrlFromUris() {
        if (uris == null || uris.trim().isEmpty()) {
            return "http://localhost:9200";
        }
        // Spring Boot 的 uris 可以是逗号分隔的多个 URI，取第一个
        String[] uriArray = uris.split(",");
        return uriArray[0].trim();
    }
    
    /**
     * 从 connection-timeout 字符串转换为毫秒数
     */
    private int getTimeoutFromString(String timeoutStr) {
        if (timeoutStr == null || timeoutStr.trim().isEmpty()) {
            return 30000;
        }
        try {
            // 支持格式：30s, 30000ms, 30
            String trimmed = timeoutStr.trim().toLowerCase();
            if (trimmed.endsWith("s")) {
                return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 1000;
            } else if (trimmed.endsWith("ms")) {
                return Integer.parseInt(trimmed.substring(0, trimmed.length() - 2));
            } else {
                return Integer.parseInt(trimmed);
            }
        } catch (Exception e) {
            logger.warn("解析超时时间失败: {}, 使用默认值 30000ms", timeoutStr);
            return 30000;
        }
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
            logger.debug("VectorDatabaseRepository未注入，使用 Spring Boot 标准配置");
            actualUrl = getUrlFromUris();
            actualApiKey = apiKey;
            actualUsername = username;
            actualPassword = password;
            actualTimeout = getTimeoutFromString(connectionTimeout);
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
            
            // 数据库没有配置，使用 Spring Boot 标准配置的默认值
            actualUrl = getUrlFromUris();
            actualApiKey = apiKey;
            actualUsername = username;
            actualPassword = password;
            actualTimeout = getTimeoutFromString(connectionTimeout);
            logger.info("数据库中没有Elasticsearch配置，使用 Spring Boot 标准配置 - URL: {}", actualUrl);
        } catch (Exception e) {
            logger.warn("从数据库加载Elasticsearch配置失败，使用 Spring Boot 标准配置: {}", e.getMessage(), e);
            actualUrl = getUrlFromUris();
            actualApiKey = apiKey;
            actualUsername = username;
            actualPassword = password;
            actualTimeout = getTimeoutFromString(connectionTimeout);
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
        return actualUrl != null ? actualUrl : getUrlFromUris();
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
     * 获取实际使用的超时时间（毫秒）
     */
    public int getTimeout() {
        return actualTimeout > 0 ? actualTimeout : getTimeoutFromString(connectionTimeout);
    }
    
    @Override
    public void run(String... args) throws Exception {
        // Elasticsearch连接测试在需要时进行，不在启动时强制测试
    }
}

