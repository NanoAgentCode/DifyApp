package com.github.app.dify.appsystemdata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.appknowledgebase.domain.VectorDatabase;
import com.github.app.dify.appknowledgebase.repository.VectorDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * Qdrant配置类
 * 注意：
 * 1. 本项目使用HTTP REST API访问Qdrant，不直接使用Qdrant Java Client
 * 2. Docker部署的Qdrant默认不需要API key，仅在启用认证时才需要配置
 * 3. 优先从数据库读取配置，如果数据库没有配置则使用application.yml的配置
 */
@Configuration
@ConfigurationProperties(prefix = "qdrant")
@DependsOn("vectorDatabaseRepository")
public class QdrantConfig implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(QdrantConfig.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    // 默认值（从application.yml读取，如果数据库没有配置则使用这些值）
    private String url = "http://localhost:6333";
    private String apiKey;
    private int timeout = 30000;
    
    // 实际使用的配置值（从数据库读取或使用默认值）
    private String actualUrl;
    private String actualApiKey;
    private int actualTimeout;
    
    // Setter方法（Spring Boot需要从application.yml读取）
    public void setUrl(String url) {
        this.url = url;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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
            actualTimeout = timeout;
            return;
        }
        
        try {
            // 先尝试查找默认的启用配置
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType("qdrant");
            if (defaultConfig.isPresent()) {
                VectorDatabase config = defaultConfig.get();
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Qdrant配置（默认） - 名称: {}, URL: {}", config.getName(), actualUrl);
                return;
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType("qdrant");
            if (!enabledConfigs.isEmpty()) {
                VectorDatabase config = enabledConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Qdrant配置（第一个启用） - 名称: {}, URL: {}", config.getName(), actualUrl);
                return;
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType("qdrant");
            if (!allConfigs.isEmpty()) {
                VectorDatabase config = allConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Qdrant配置（任意） - 名称: {}, URL: {}, 启用状态: {}", 
                        config.getName(), actualUrl, config.getEnabled());
                return;
            }
            
            // 数据库没有配置，使用application.yml的默认值
            actualUrl = url;
            actualApiKey = apiKey;
            actualTimeout = timeout;
            logger.info("数据库中没有Qdrant配置，使用application.yml配置 - URL: {}", actualUrl);
        } catch (Exception e) {
            logger.warn("从数据库加载Qdrant配置失败，使用application.yml配置: {}", e.getMessage(), e);
            actualUrl = url;
            actualApiKey = apiKey;
            actualTimeout = timeout;
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
     * 获取实际使用的超时时间
     */
    public int getTimeout() {
        return actualTimeout > 0 ? actualTimeout : timeout;
    }
    
    @Override
    public void run(String... args) throws Exception {
        testQdrantConnection();
    }
    
    /**
     * 测试Qdrant连接
     */
    private void testQdrantConnection() {
        logger.info("==========================================");
        logger.info("开始测试Qdrant向量数据库连接...");
        logger.info("Qdrant URL: {}", getUrl());
        logger.info("API Key: {}", getApiKey() != null && !getApiKey().trim().isEmpty() ? "已配置" : "未配置");
        logger.info("超时时间: {} 毫秒", getTimeout());
        
        try {
            String finalUrl = getUrl() != null ? getUrl() : "http://localhost:6333";
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(finalUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            
            String finalApiKey = getApiKey();
            if (finalApiKey != null && !finalApiKey.trim().isEmpty()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + finalApiKey);
            }
            
            WebClient webClient = builder.build();
            
            // 测试健康检查端点（Qdrant使用/healthz端点）
            long startTime = System.currentTimeMillis();
            String healthResponseText = null;
            try {
                healthResponseText = webClient
                        .get()
                        .uri("/healthz")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(getTimeout()))
                        .block();
            } catch (Exception e) {
                // 如果/healthz失败，尝试根路径
                logger.debug("尝试/healthz端点失败，尝试根路径: {}", e.getMessage());
                try {
                    healthResponseText = webClient
                        .get()
                        .uri("/")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(getTimeout()))
                        .block();
                } catch (Exception e2) {
                    throw e; // 抛出原始异常
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (healthResponseText != null && !healthResponseText.trim().isEmpty()) {
                logger.info("Qdrant健康检查成功！");
                logger.info("响应时间: {} 毫秒", duration);
                logger.debug("健康检查响应: {}", healthResponseText);
                
                // 尝试解析JSON响应（如果返回的是JSON）
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> healthResponse = objectMapper.readValue(healthResponseText, Map.class);
                    if (healthResponse.containsKey("status")) {
                        logger.info("服务状态: {}", healthResponse.get("status"));
                    }
                    if (healthResponse.containsKey("version")) {
                        logger.info("Qdrant版本: {}", healthResponse.get("version"));
                    }
                } catch (Exception e) {
                    // 如果不是JSON格式，忽略解析错误
                    logger.debug("健康检查响应不是JSON格式，跳过解析");
                }
                
                // 测试获取集合列表（验证API权限）
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> collectionsResponse = webClient
                            .get()
                            .uri("/collections")
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(Duration.ofMillis(getTimeout()))
                            .block();
                    
                    if (collectionsResponse != null && collectionsResponse.containsKey("result")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = (Map<String, Object>) collectionsResponse.get("result");
                        if (result.containsKey("collections")) {
                            int collectionCount = ((java.util.List<?>) result.get("collections")).size();
                            logger.info("当前集合数量: {}", collectionCount);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("获取集合列表失败（可能是权限问题）: {}", e.getMessage());
                }
                
                logger.info("==========================================");
            } else {
                logger.warn("Qdrant健康检查返回空响应");
            }
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            logger.error("==========================================");
            logger.error("Qdrant连接失败！");
            logger.error("HTTP状态码: {}", e.getStatusCode());
            logger.error("错误信息: {}", e.getMessage());
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null) {
                logger.error("响应体: {}", responseBody);
            }
            logger.error("==========================================");
            logger.warn("应用将继续启动，但向量数据库功能可能无法使用");
            logger.warn("请检查Qdrant服务是否正在运行，URL配置是否正确");
        } catch (Exception e) {
            // 检查是否是超时异常
            Throwable cause = e.getCause();
            boolean isTimeout = e.getMessage() != null && e.getMessage().contains("timeout") ||
                               cause != null && (cause instanceof java.util.concurrent.TimeoutException ||
                                                 cause instanceof java.net.SocketTimeoutException ||
                                                 cause.getMessage() != null && cause.getMessage().contains("timeout"));
            
            if (isTimeout) {
                logger.error("==========================================");
                logger.error("Qdrant连接超时！");
                logger.error("超时时间: {} 毫秒", getTimeout());
                logger.error("错误信息: {}", e.getMessage());
                logger.error("==========================================");
                logger.warn("应用将继续启动，但向量数据库功能可能无法使用");
                logger.warn("请检查Qdrant服务是否正在运行，网络连接是否正常");
            } else {
                logger.error("==========================================");
                logger.error("Qdrant连接测试失败！");
                logger.error("错误类型: {}", e.getClass().getSimpleName());
                logger.error("错误信息: {}", e.getMessage());
                logger.error("==========================================", e);
                logger.warn("应用将继续启动，但向量数据库功能可能无法使用");
                logger.warn("请检查Qdrant配置和服务状态");
            }
        }
    }
}