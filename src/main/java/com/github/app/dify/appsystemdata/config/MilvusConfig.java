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
 * Milvus配置类
 * 注意：
 * 1. 本项目使用HTTP REST API访问Milvus
 * 2. 优先从数据库读取配置，如果数据库没有配置则使用application.yml的配置
 */
@Configuration
@ConfigurationProperties(prefix = "milvus")
@DependsOn("vectorDatabaseRepository")
public class MilvusConfig implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(MilvusConfig.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    // 默认值（从application.yml读取，如果数据库没有配置则使用这些值）
    private String url = "http://localhost:19530";
    private String apiKey; // 可选，如果Milvus启用了认证
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
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType("milvus");
            if (defaultConfig.isPresent()) {
                VectorDatabase config = defaultConfig.get();
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Milvus配置（默认） - 名称: {}, 类型: {}, URL: {}", 
                        config.getName(), config.getType(), actualUrl);
                return;
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType("milvus");
            if (!enabledConfigs.isEmpty()) {
                VectorDatabase config = enabledConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Milvus配置（第一个启用） - 名称: {}, 类型: {}, URL: {}", 
                        config.getName(), config.getType(), actualUrl);
                return;
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType("milvus");
            if (!allConfigs.isEmpty()) {
                VectorDatabase config = allConfigs.get(0);
                actualUrl = config.getUrl();
                actualApiKey = config.getApiKey();
                actualTimeout = config.getTimeout() != null ? config.getTimeout() : 30000;
                logger.info("从数据库加载Milvus配置（任意） - 名称: {}, 类型: {}, URL: {}, 启用状态: {}", 
                        config.getName(), config.getType(), actualUrl, config.getEnabled());
                return;
            }
            
            // 数据库没有配置，使用application.yml的默认值
            actualUrl = url;
            actualApiKey = apiKey;
            actualTimeout = timeout;
            logger.info("数据库中没有Milvus配置，使用application.yml配置 - URL: {}", actualUrl);
        } catch (Exception e) {
            logger.warn("从数据库加载Milvus配置失败，使用application.yml配置: {}", e.getMessage(), e);
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
        testMilvusConnection();
    }
    
    /**
     * 测试Milvus连接
     */
    private void testMilvusConnection() {
        logger.info("==========================================");
        logger.info("开始测试Milvus向量数据库连接...");
        logger.info("Milvus URL: {}", getUrl());
        logger.info("API Key: {}", getApiKey() != null && !getApiKey().trim().isEmpty() ? "已配置" : "未配置");
        logger.info("超时时间: {} 毫秒", getTimeout());
        
        try {
            String finalUrl = getUrl() != null ? getUrl() : "http://localhost:19530";
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(finalUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            
            String finalApiKey = getApiKey();
            if (finalApiKey != null && !finalApiKey.trim().isEmpty()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + finalApiKey);
            }
            
            WebClient webClient = builder.build();
            
            // 测试健康检查端点（尝试多个端点，不同版本的Milvus可能使用不同的端点）
            long startTime = System.currentTimeMillis();
            String[] healthEndpoints = {"/healthz", "/api/v1/health", "/health", "/api/v1/healthz"};
            String healthResponseText = null;
            String successfulEndpoint = null;
            String successfulBaseUrl = null;
            
            // 首先尝试19530端口的端点
            for (String endpoint : healthEndpoints) {
                try {
                    healthResponseText = webClient
                            .get()
                            .uri(endpoint)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofMillis(getTimeout()))
                            .block();
                    
                    // 如果成功获取响应且不为空，则连接成功
                    if (healthResponseText != null && !healthResponseText.trim().isEmpty()) {
                        successfulEndpoint = endpoint;
                        successfulBaseUrl = finalUrl;
                        break;
                    }
                } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                    // 如果是404错误，继续尝试下一个端点
                    if (e.getStatusCode().value() == 404) {
                        logger.debug("端点 {} 返回404，尝试下一个端点", endpoint);
                        continue;
                    }
                    // 其他HTTP错误，记录并继续尝试
                    logger.debug("端点 {} 返回错误 {}: {}", endpoint, e.getStatusCode(), e.getMessage());
                } catch (Exception e) {
                    // 其他异常（如超时、网络错误等），记录并继续尝试
                    logger.debug("端点 {} 访问异常: {}", endpoint, e.getMessage());
                }
            }
            
            // 如果19530端口的端点都失败，尝试9091端口（metrics端口）的健康检查
            if (successfulEndpoint == null) {
                try {
                    // 从URL中提取协议和主机，替换端口为9091
                    java.net.URL urlObj = new java.net.URL(finalUrl);
                    String metricsUrl = urlObj.getProtocol() + "://" + urlObj.getHost() + ":9091";
                    logger.debug("19530端口端点都失败，尝试9091端口（metrics端口）的健康检查: {}", metricsUrl);
                    
                    WebClient.Builder metricsBuilder = WebClient.builder()
                            .baseUrl(metricsUrl)
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    
                    if (finalApiKey != null && !finalApiKey.trim().isEmpty()) {
                        metricsBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + finalApiKey);
                    }
                    
                    WebClient metricsWebClient = metricsBuilder.build();
                    
                    try {
                        healthResponseText = metricsWebClient
                                .get()
                                .uri("/healthz")
                                .retrieve()
                                .bodyToMono(String.class)
                                .timeout(Duration.ofMillis(getTimeout()))
                                .block();
                        
                        if (healthResponseText != null && !healthResponseText.trim().isEmpty()) {
                            successfulEndpoint = "/healthz";
                            successfulBaseUrl = metricsUrl;
                            logger.debug("Milvus健康检查成功，使用的端点: /healthz (URL: {})", metricsUrl);
                        }
                    } catch (Exception e) {
                        logger.debug("9091端口健康检查也失败: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    logger.debug("无法构建9091端口URL: {}", e.getMessage());
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (healthResponseText != null && !healthResponseText.trim().isEmpty()) {
                logger.info("Milvus健康检查成功！");
                if (successfulEndpoint != null) {
                    logger.info("使用的健康检查端点: {} (URL: {})", successfulEndpoint, successfulBaseUrl != null ? successfulBaseUrl : finalUrl);
                }
                logger.info("响应时间: {} 毫秒", duration);
                logger.debug("健康检查响应: {}", healthResponseText);
                
                // 尝试解析JSON响应（如果返回的是JSON）
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> healthResponse = objectMapper.readValue(healthResponseText, Map.class);
                    if (healthResponse.containsKey("status")) {
                        logger.info("服务状态: {}", healthResponse.get("status"));
                    }
                } catch (Exception e) {
                    // 如果不是JSON格式，忽略解析错误
                    logger.debug("健康检查响应不是JSON格式，跳过解析");
                }
                
                logger.info("==========================================");
            } else {
                logger.warn("Milvus健康检查返回空响应");
            }
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            logger.error("==========================================");
            logger.error("Milvus连接失败！");
            logger.error("HTTP状态码: {}", e.getStatusCode());
            logger.error("错误信息: {}", e.getMessage());
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null) {
                logger.error("响应体: {}", responseBody);
            }
            logger.error("==========================================");
            logger.warn("应用将继续启动，但向量数据库功能可能无法使用");
            logger.warn("请检查Milvus服务是否正在运行，URL配置是否正确");
        } catch (Exception e) {
            // 检查是否是超时异常
            Throwable cause = e.getCause();
            boolean isTimeout = e.getMessage() != null && e.getMessage().contains("timeout") ||
                               cause != null && (cause instanceof java.util.concurrent.TimeoutException ||
                                                 cause instanceof java.net.SocketTimeoutException ||
                                                 cause.getMessage() != null && cause.getMessage().contains("timeout"));
            
            if (isTimeout) {
                logger.error("==========================================");
                logger.error("Milvus连接超时！");
                logger.error("超时时间: {} 毫秒", getTimeout());
                logger.error("错误信息: {}", e.getMessage());
                logger.error("==========================================");
                logger.warn("应用将继续启动，但向量数据库功能可能无法使用");
                logger.warn("请检查Milvus服务是否正在运行，网络连接是否正常");
            } else {
                logger.error("==========================================");
                logger.error("Milvus连接测试失败！");
                logger.error("错误类型: {}", e.getClass().getSimpleName());
                logger.error("错误信息: {}", e.getMessage());
                logger.error("==========================================", e);
                logger.warn("应用将继续启动，但向量数据库功能可能无法使用");
                logger.warn("请检查Milvus配置和服务状态");
            }
        }
    }
}