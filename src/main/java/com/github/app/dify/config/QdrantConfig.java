package com.github.app.dify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Qdrant配置类
 * 注意：
 * 1. 本项目使用HTTP REST API访问Qdrant，不直接使用Qdrant Java Client
 * 2. Docker部署的Qdrant默认不需要API key，仅在启用认证时才需要配置
 */
@Configuration
@ConfigurationProperties(prefix = "qdrant")
public class QdrantConfig implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(QdrantConfig.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String url = "http://localhost:6333";
    private String apiKey;
    private int timeout = 30000;
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
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
        logger.info("Qdrant URL: {}", url);
        logger.info("API Key: {}", apiKey != null && !apiKey.trim().isEmpty() ? "已配置" : "未配置");
        logger.info("超时时间: {} 毫秒", timeout);
        
        try {
            String finalUrl = url != null ? url : "http://localhost:6333";
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(finalUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            
            String finalApiKey = apiKey;
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
                        .timeout(Duration.ofMillis(timeout))
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
                            .timeout(Duration.ofMillis(timeout))
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
                            .timeout(Duration.ofMillis(timeout))
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
                logger.error("超时时间: {} 毫秒", timeout);
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

