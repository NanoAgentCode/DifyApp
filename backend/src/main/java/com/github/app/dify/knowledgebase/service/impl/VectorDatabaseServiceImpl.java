package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.system.config.FaissConfig;
import com.github.app.dify.system.config.MilvusConfig;
import com.github.app.dify.system.config.QdrantConfig;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import com.github.app.dify.knowledgebase.req.TestVectorDatabaseConnectionRequest;
import com.github.app.dify.knowledgebase.req.VectorDatabaseRequest;
import com.github.app.dify.knowledgebase.resp.VectorDatabaseResp;
import com.github.app.dify.knowledgebase.service.VectorDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseConverterUtil;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseDateTimeUtil;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseSoftDeleteUtil;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseWebClientUtil;
import reactor.core.publisher.Mono;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * 向量数据库配置服务实现
 */
@Service
public class VectorDatabaseServiceImpl implements VectorDatabaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseServiceImpl.class);
    
    @Autowired
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired(required = false)
    private QdrantConfig qdrantConfig;
    
    @Autowired(required = false)
    private MilvusConfig milvusConfig;
    
    @Autowired(required = false)
    private FaissConfig faissConfig;
    
    @Autowired
    private com.github.app.dify.knowledgebase.util.VectorDatabaseConfigHelper vectorDatabaseConfigHelper;
    
    @Autowired(required = false)
    private com.github.app.dify.system.config.WeaviateConfig weaviateConfig;
    
    @Override
    @Cacheable(value = "vectorDatabase", key = "'all'")
    public List<VectorDatabaseResp> getAllConfigs() {
        List<VectorDatabase> configs = vectorDatabaseRepository.findAllActive();
        return configs.stream()
                .map(KnowledgeBaseConverterUtil::convertToResp)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<VectorDatabaseResp> getConfigsByType(String type) {
        List<VectorDatabase> configs = vectorDatabaseRepository.findByType(type);
        return configs.stream()
                .map(KnowledgeBaseConverterUtil::convertToResp)
                .collect(Collectors.toList());
    }
    
    @Override
    @Cacheable(value = "vectorDatabase", key = "#type + '_default'")
    public VectorDatabase getDefaultConfigByType(String type) {
        Optional<VectorDatabase> optional = vectorDatabaseRepository.findDefaultEnabledByType(type);
        if (optional.isPresent()) {
            return optional.get();
        }
        
        // 如果没有默认配置，返回第一个启用的配置
        List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType(type);
        if (!enabledConfigs.isEmpty()) {
            return enabledConfigs.get(0);
        }
        
        return null;
    }
    
    @Override
    @Transactional
    @CacheEvict(value = {"vectorDatabase"}, allEntries = true)
    public Object updateConfig(VectorDatabaseRequest request) {
        String action = request.getAction();
        
        if ("add".equals(action)) {
            return addConfig(request);
        } else if ("update".equals(action)) {
            return updateConfigInternal(request);
        } else if ("delete".equals(action)) {
            deleteConfig(request.getConfigId());
            return null;
        } else if ("setDefault".equals(action)) {
            setDefaultConfig(request.getConfigId());
            return null;
        } else if ("toggleEnabled".equals(action)) {
            toggleEnabled(request.getConfigId(), request.getEnabled());
            return null;
        } else {
            throw new RuntimeException("不支持的操作类型: " + action);
        }
    }
    
    @Override
    public void testConnection(TestVectorDatabaseConnectionRequest request) {
        String type = request.getType();
        String url = request.getUrl();
        String apiKey = request.getApiKey();
        String extraConfig = request.getExtraConfig();
        Integer timeout = request.getTimeout() != null ? request.getTimeout() : 30000;
        
        try {
            if ("qdrant".equalsIgnoreCase(type)) {
                testQdrantConnection(url, apiKey, timeout);
            } else if ("milvus".equalsIgnoreCase(type)) {
                testMilvusConnection(url, apiKey, timeout);
            } else if ("faiss".equalsIgnoreCase(type)) {
                testFaissConnection(url);
            } else if ("chroma".equalsIgnoreCase(type)) {
                testChromaConnection(url, apiKey, timeout);
            } else if ("weaviate".equalsIgnoreCase(type)) {
                testWeaviateConnection(url, apiKey, timeout);
            } else if ("elasticsearch".equalsIgnoreCase(type)) {
                testElasticsearchConnection(url, apiKey, extraConfig, timeout);
            } else if ("pgvector".equalsIgnoreCase(type)) {
                testPgVectorConnection(url, apiKey, extraConfig, timeout);
            } else {
                throw new RuntimeException("不支持的数据库类型: " + type);
            }
        } catch (Exception e) {
            logger.error("测试向量数据库连接失败 - 类型: {}, URL: {}", type, url, e);
            throw new RuntimeException("测试连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加配置
     */
    private VectorDatabaseResp addConfig(VectorDatabaseRequest request) {
        VectorDatabaseRequest.DatabaseInfo info = request.getDatabase();
        if (info == null) {
            throw new RuntimeException("配置信息不能为空");
        }
        
        VectorDatabase config = new VectorDatabase();
        config.setName(info.getName());
        config.setType(info.getType());
        config.setUrl(info.getUrl());
        config.setApiKey(info.getApiKey());
        config.setTimeout(info.getTimeout() != null ? info.getTimeout() : 30000);
        config.setExtraConfig(info.getExtraConfig());
        config.setEnabled(info.getEnabled() != null ? info.getEnabled() : true);
        config.setIsDefault(false); // 新添加的配置默认不是默认配置
        config.setAllowCreateKnowledgeBase(info.getAllowCreateKnowledgeBase() != null ? info.getAllowCreateKnowledgeBase() : true); // 默认允许新建知识库
        config.setDescription(info.getDescription());
        KnowledgeBaseDateTimeUtil.setCreateAndUpdateTime(config);
        config.setDeleted(0);
        
        // 如果设置为默认，需要先取消同类型其他配置的默认状态
        if (info.getEnabled() != null && info.getEnabled()) {
            // 检查是否已有默认配置
            Optional<VectorDatabase> existingDefault = vectorDatabaseRepository.findDefaultEnabledByType(info.getType());
            if (existingDefault.isPresent() && !existingDefault.get().getId().equals(config.getId())) {
                // 如果新配置要设为默认，取消旧的默认配置
                // 这里暂时不自动设置，需要用户手动设置默认
            }
        }
        
        VectorDatabase saved = vectorDatabaseRepository.save(config);
        logger.info("添加向量数据库配置成功 - ID: {}, 名称: {}, 类型: {}", 
                saved.getId(), saved.getName(), saved.getType());
        
        // 如果新添加的是默认配置，重新加载配置
        if (saved.getIsDefault() != null && saved.getIsDefault() && saved.getEnabled()) {
            reloadConfig(saved.getType());
        }
        
        return KnowledgeBaseConverterUtil.convertToResp(saved);
    }
    
    /**
     * 更新配置（内部方法）
     */
    private VectorDatabaseResp updateConfigInternal(VectorDatabaseRequest request) {
        VectorDatabaseRequest.DatabaseInfo info = request.getDatabase();
        if (info == null || info.getId() == null) {
            throw new RuntimeException("配置ID不能为空");
        }
        
        Optional<VectorDatabase> optional = vectorDatabaseRepository.findById(info.getId());
        if (!optional.isPresent()) {
            throw new RuntimeException("配置不存在: " + info.getId());
        }
        
        VectorDatabase config = optional.get();
        if (config.getDeleted() != null && config.getDeleted() == 1) {
            throw new RuntimeException("配置已删除");
        }
        
        // 更新字段
        config.setName(info.getName());
        config.setType(info.getType());
        config.setUrl(info.getUrl());
        if (info.getApiKey() != null) {
            config.setApiKey(info.getApiKey());
        }
        if (info.getTimeout() != null) {
            config.setTimeout(info.getTimeout());
        }
        if (info.getExtraConfig() != null) {
            config.setExtraConfig(info.getExtraConfig());
        }
        if (info.getEnabled() != null) {
            config.setEnabled(info.getEnabled());
        }
        if (info.getDescription() != null) {
            config.setDescription(info.getDescription());
        }
        if (info.getAllowCreateKnowledgeBase() != null) {
            config.setAllowCreateKnowledgeBase(info.getAllowCreateKnowledgeBase());
        }
        KnowledgeBaseDateTimeUtil.setUpdateTime(config);
        
        VectorDatabase saved = vectorDatabaseRepository.save(config);
        logger.info("更新向量数据库配置成功 - ID: {}, 名称: {}, 类型: {}", 
                saved.getId(), saved.getName(), saved.getType());
        
        // 如果更新的是默认配置，重新加载配置
        if (saved.getIsDefault() != null && saved.getIsDefault() && saved.getEnabled()) {
            reloadConfig(saved.getType());
        }
        
        return KnowledgeBaseConverterUtil.convertToResp(saved);
    }
    
    /**
     * 删除配置
     */
    private void deleteConfig(Long configId) {
        Optional<VectorDatabase> optional = vectorDatabaseRepository.findById(configId);
        if (!optional.isPresent()) {
            throw new RuntimeException("配置不存在: " + configId);
        }
        
        VectorDatabase config = optional.get();
        KnowledgeBaseSoftDeleteUtil.softDelete(config, vectorDatabaseRepository);
        
        logger.info("删除向量数据库配置成功 - ID: {}, 名称: {}", configId, config.getName());
    }
    
    /**
     * 设置默认配置
     */
    private void setDefaultConfig(Long configId) {
        Optional<VectorDatabase> optional = vectorDatabaseRepository.findById(configId);
        if (!optional.isPresent()) {
            throw new RuntimeException("配置不存在: " + configId);
        }
        
        VectorDatabase config = optional.get();
        if (config.getDeleted() != null && config.getDeleted() == 1) {
            throw new RuntimeException("配置已删除");
        }
        
        if (!config.getEnabled()) {
            throw new RuntimeException("只有启用的配置才能设置为默认");
        }
        
        // 取消所有其他配置的默认状态（全局单选，只能有一个默认配置）
        List<VectorDatabase> allConfigs = vectorDatabaseRepository.findAllActive();
        for (VectorDatabase c : allConfigs) {
            if (c.getIsDefault() != null && c.getIsDefault() && !c.getId().equals(configId)) {
                c.setIsDefault(false);
                KnowledgeBaseDateTimeUtil.setUpdateTime(c);
                vectorDatabaseRepository.save(c);
            }
        }
        
        // 设置当前配置为默认
        config.setIsDefault(true);
        KnowledgeBaseDateTimeUtil.setUpdateTime(config);
        vectorDatabaseRepository.save(config);
        
        logger.info("设置默认向量数据库配置成功 - ID: {}, 名称: {}, 类型: {}", 
                configId, config.getName(), config.getType());
        
        // 重新加载配置
        reloadConfig(config.getType());
    }
    
    /**
     * 切换启用状态
     */
    private void toggleEnabled(Long configId, Boolean enabled) {
        Optional<VectorDatabase> optional = vectorDatabaseRepository.findById(configId);
        if (!optional.isPresent()) {
            throw new RuntimeException("配置不存在: " + configId);
        }
        
        VectorDatabase config = optional.get();
        if (config.getDeleted() != null && config.getDeleted() == 1) {
            throw new RuntimeException("配置已删除");
        }
        
        config.setEnabled(enabled);
        KnowledgeBaseDateTimeUtil.setUpdateTime(config);
        
        // 如果禁用的是默认配置，需要取消默认状态
        if (!enabled && config.getIsDefault() != null && config.getIsDefault()) {
            config.setIsDefault(false);
        }
        
        vectorDatabaseRepository.save(config);
        
        logger.info("切换向量数据库配置启用状态成功 - ID: {}, 名称: {}, 启用: {}", 
                configId, config.getName(), enabled);
        
        // 如果切换的是默认配置，重新加载配置
        if (config.getIsDefault() != null && config.getIsDefault()) {
            reloadConfig(config.getType());
        }
    }
    
    /**
     * 重新加载配置（通知配置类重新从数据库读取）
     */
    private void reloadConfig(String type) {
        try {
            if ("qdrant".equalsIgnoreCase(type) && qdrantConfig != null) {
                qdrantConfig.reload();
            } else if ("milvus".equalsIgnoreCase(type) && milvusConfig != null) {
                milvusConfig.reload();
            } else if ("faiss".equalsIgnoreCase(type) && faissConfig != null) {
                faissConfig.reload();
            } else if ("weaviate".equalsIgnoreCase(type) && weaviateConfig != null) {
                weaviateConfig.reload();
            } else if ("elasticsearch".equalsIgnoreCase(type)) {
                // Elasticsearch配置重新加载在需要时进行
            }
        } catch (Exception e) {
            logger.warn("重新加载{}配置失败: {}", type, e.getMessage());
        }
    }
    
    /**
     * 测试Qdrant连接
     */
    private void testQdrantConnection(String url, String apiKey, int timeout) {
        WebClient webClient = KnowledgeBaseWebClientUtil.createBuilderWithApiKey(url, apiKey).build();
        
        String healthResponse = webClient
                .get()
                .uri("/healthz")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .block();
        
        if (healthResponse == null || healthResponse.trim().isEmpty()) {
            throw new RuntimeException("Qdrant健康检查返回空响应");
        }
    }
    
    /**
     * 测试Milvus连接
     */
    private void testMilvusConnection(String url, String apiKey, int timeout) {
        // 验证 URL 格式（Milvus 使用 gRPC）
        if (url == null || url.trim().isEmpty()) {
            throw new RuntimeException("Milvus URL 不能为空，请配置有效的 HTTP URL（例如：http://localhost:19530）");
        }
        
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            throw new RuntimeException(
                String.format("Milvus URL 必须是有效的 HTTP/HTTPS 地址，当前配置为: %s。请配置为 HTTP URL（例如：http://localhost:19530）", 
                    trimmedUrl));
        }
        
        WebClient webClient = KnowledgeBaseWebClientUtil.createBuilderWithApiKey(trimmedUrl, apiKey).build();
        
        // 尝试多个健康检查端点（不同版本的Milvus可能使用不同的端点）
        // 首先尝试19530端口的端点
        String[] healthEndpoints = {"/healthz", "/api/v1/health", "/health", "/api/v1/healthz"};
        String healthResponse = null;
        Exception lastException = null;
        String successfulEndpoint = null;
        
        for (String endpoint : healthEndpoints) {
            try {
                healthResponse = webClient
                        .get()
                        .uri(endpoint)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(timeout))
                        .block();
                
                // 如果成功获取响应且不为空，则连接成功
                if (healthResponse != null && !healthResponse.trim().isEmpty()) {
                    successfulEndpoint = endpoint;
                    logger.debug("Milvus健康检查成功，使用的端点: {} (URL: {})", endpoint, trimmedUrl);
                    break;
                }
            } catch (WebClientResponseException e) {
                // 如果是404错误，继续尝试下一个端点
                if (e.getStatusCode().value() == 404) {
                    logger.debug("端点 {} 返回404，尝试下一个端点", endpoint);
                    lastException = e;
                    continue;
                }
                // 其他HTTP错误，记录并继续尝试
                logger.debug("端点 {} 返回错误 {}: {}", endpoint, e.getStatusCode(), e.getMessage());
                lastException = e;
            } catch (Exception e) {
                // 其他异常（如超时、网络错误等），记录并继续尝试
                logger.debug("端点 {} 访问异常: {}", endpoint, e.getMessage());
                lastException = e;
            }
        }
        
        // 如果19530端口的端点都失败，尝试9091端口（metrics端口）的健康检查
        if (successfulEndpoint == null) {
            try {
                // 从URL中提取协议和主机，替换端口为9091
                URL urlObj = new URL(trimmedUrl);
                String metricsUrl = urlObj.getProtocol() + "://" + urlObj.getHost() + ":9091";
                logger.debug("19530端口端点都失败，尝试9091端口（metrics端口）的健康检查: {}", metricsUrl);
                
                WebClient metricsWebClient = KnowledgeBaseWebClientUtil.createBuilderWithApiKey(metricsUrl, apiKey).build();
                
                try {
                    healthResponse = metricsWebClient
                            .get()
                            .uri("/healthz")
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                    
                    if (healthResponse != null && !healthResponse.trim().isEmpty()) {
                        successfulEndpoint = "/healthz";
                        logger.debug("Milvus健康检查成功，使用的端点: /healthz (URL: {})", metricsUrl);
                    }
                } catch (Exception e) {
                    logger.debug("9091端口健康检查也失败: {}", e.getMessage());
                    lastException = e;
                }
            } catch (Exception e) {
                logger.debug("无法构建9091端口URL: {}", e.getMessage());
            }
        }
        
        // 如果找到成功的端点，返回
        if (successfulEndpoint != null) {
            return;
        }
        
        // 所有端点都失败
        if (lastException != null) {
            if (lastException instanceof WebClientResponseException) {
                WebClientResponseException webEx = (WebClientResponseException) lastException;
                throw new RuntimeException(
                    String.format("Milvus连接失败: %d %s。已尝试端点: %s (19530端口) 和 /healthz (9091端口)。请检查Milvus服务是否正在运行，URL是否正确。", 
                        webEx.getStatusCode().value(), 
                        webEx.getStatusText(),
                        String.join(", ", healthEndpoints)));
            } else {
                throw new RuntimeException(
                    String.format("Milvus连接失败: %s。已尝试端点: %s (19530端口) 和 /healthz (9091端口)。请检查Milvus服务是否正在运行，URL是否正确。", 
                        lastException.getMessage(),
                        String.join(", ", healthEndpoints)));
            }
        } else {
            throw new RuntimeException(
                String.format("Milvus健康检查返回空响应。已尝试端点: %s (19530端口) 和 /healthz (9091端口)。请检查Milvus服务是否正在运行。", 
                    String.join(", ", healthEndpoints)));
        }
    }
    
    /**
     * 测试Chroma连接
     */
    private void testChromaConnection(String url, String apiKey, int timeout) {
        WebClient webClient = KnowledgeBaseWebClientUtil.createBuilderWithApiKey(url, apiKey).build();
        
        // 尝试多个端点，因为不同版本的Chroma可能使用不同的端点
        String[] healthEndpoints = {"/api/v1/version", "/", "/api/v1/heartbeat"};
        String healthResponse = null;
        String successfulEndpoint = null;
        Exception lastException = null;
        
        for (String endpoint : healthEndpoints) {
            try {
                healthResponse = webClient
                        .get()
                        .uri(endpoint)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                clientResponse -> {
                                    // 对于410 Gone，继续尝试下一个端点
                                    if (clientResponse.statusCode().value() == 410) {
                                        return Mono.empty();
                                    }
                                    return Mono.error(new RuntimeException(
                                            "Chroma连接失败: HTTP " + clientResponse.statusCode()));
                                })
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(timeout))
                        .block();
                
                if (healthResponse != null && !healthResponse.trim().isEmpty()) {
                    successfulEndpoint = endpoint;
                    break;
                }
            } catch (WebClientResponseException e) {
                // 如果是410 Gone，继续尝试下一个端点
                if (e.getStatusCode().value() == 410) {
                    logger.debug("端点 {} 返回410 Gone（已废弃），尝试下一个端点", endpoint);
                    lastException = e;
                    continue;
                }
                // 如果是404，继续尝试下一个端点
                if (e.getStatusCode().value() == 404) {
                    logger.debug("端点 {} 返回404，尝试下一个端点", endpoint);
                    lastException = e;
                    continue;
                }
                // 其他HTTP错误，记录并继续尝试
                logger.debug("端点 {} 返回错误 {}: {}", endpoint, e.getStatusCode(), e.getMessage());
                lastException = e;
            } catch (Exception e) {
                // 其他异常（如超时、网络错误等），记录并继续尝试
                logger.debug("端点 {} 访问异常: {}", endpoint, e.getMessage());
                lastException = e;
            }
        }
        
        // 如果找到成功的端点，返回
        if (successfulEndpoint != null && healthResponse != null && !healthResponse.trim().isEmpty()) {
            logger.debug("Chroma连接测试成功，使用的端点: {}", successfulEndpoint);
            return;
        }
        
        // 所有端点都失败
        if (lastException != null) {
            if (lastException instanceof WebClientResponseException) {
                WebClientResponseException webEx = (WebClientResponseException) lastException;
                throw new RuntimeException(
                    String.format("Chroma连接失败: %d %s。已尝试端点: %s。请检查Chroma服务是否正在运行，URL是否正确。", 
                        webEx.getStatusCode().value(), 
                        webEx.getStatusText(),
                        String.join(", ", healthEndpoints)));
            } else {
                throw new RuntimeException(
                    String.format("Chroma连接失败: %s。已尝试端点: %s。请检查Chroma服务是否正在运行，URL是否正确。", 
                        lastException.getMessage(),
                        String.join(", ", healthEndpoints)));
            }
        } else {
            throw new RuntimeException(
                String.format("Chroma健康检查返回空响应。已尝试端点: %s。请检查Chroma服务是否正在运行。", 
                    String.join(", ", healthEndpoints)));
        }
    }
    
    /**
     * 测试FAISS连接（检查路径是否存在）
     */
    private void testFaissConnection(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            // 尝试创建目录
            boolean created = dir.mkdirs();
            if (!created) {
                throw new RuntimeException("FAISS路径不存在且无法创建: " + path);
            }
        }
        if (!dir.isDirectory()) {
            throw new RuntimeException("FAISS路径不是目录: " + path);
        }
        if (!dir.canWrite()) {
            throw new RuntimeException("FAISS路径不可写: " + path);
        }
    }
    
    /**
     * 测试Weaviate连接
     */
    private void testWeaviateConnection(String url, String apiKey, int timeout) {
        WebClient webClient = KnowledgeBaseWebClientUtil.createBuilderWithCustomAuth(url, apiKey, "X-API-Key").build();
        
        // 尝试多个健康检查端点
        String[] healthEndpoints = {"/v1/meta", "/.well-known/ready", "/v1/.well-known/ready"};
        String healthResponse = null;
        String successfulEndpoint = null;
        Exception lastException = null;
        
        for (String endpoint : healthEndpoints) {
            try {
                if (endpoint.equals("/v1/meta")) {
                    // /v1/meta返回JSON
                    ParameterizedTypeReference<Map<String, Object>> typeRef = 
                            new ParameterizedTypeReference<Map<String, Object>>() {};
                    Map<String, Object> metaResponse = webClient
                            .get()
                            .uri(endpoint)
                            .retrieve()
                            .bodyToMono(typeRef)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                    
                    if (metaResponse != null) {
                        healthResponse = "OK";
                        successfulEndpoint = endpoint;
                        break;
                    }
                } else {
                    // 其他端点返回文本
                    healthResponse = webClient
                            .get()
                            .uri(endpoint)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                    
                    if (healthResponse != null && !healthResponse.trim().isEmpty()) {
                        successfulEndpoint = endpoint;
                        break;
                    }
                }
            } catch (WebClientResponseException e) {
                // 如果是404，继续尝试下一个端点
                if (e.getStatusCode().value() == 404) {
                    logger.debug("端点 {} 返回404，尝试下一个端点", endpoint);
                    lastException = e;
                    continue;
                }
                // 其他HTTP错误，记录并继续尝试
                logger.debug("端点 {} 返回错误 {}: {}", endpoint, e.getStatusCode(), e.getMessage());
                lastException = e;
            } catch (Exception e) {
                // 其他异常（如超时、网络错误等），记录并继续尝试
                logger.debug("端点 {} 访问异常: {}", endpoint, e.getMessage());
                lastException = e;
            }
        }
        
        // 如果找到成功的端点，返回
        if (successfulEndpoint != null && healthResponse != null) {
            return;
        }
        
        // 所有端点都失败
        if (lastException != null) {
            if (lastException instanceof WebClientResponseException) {
                WebClientResponseException webEx = (WebClientResponseException) lastException;
                throw new RuntimeException(
                    String.format("Weaviate连接失败: %d %s。已尝试端点: %s。请检查Weaviate服务是否正在运行，URL是否正确。", 
                        webEx.getStatusCode().value(), 
                        webEx.getStatusText(),
                        String.join(", ", healthEndpoints)));
            } else {
                throw new RuntimeException(
                    String.format("Weaviate连接失败: %s。已尝试端点: %s。请检查Weaviate服务是否正在运行，URL是否正确。", 
                        lastException.getMessage(),
                        String.join(", ", healthEndpoints)));
            }
        } else {
            throw new RuntimeException(
                String.format("Weaviate健康检查返回空响应。已尝试端点: %s。请检查Weaviate服务是否正在运行。", 
                    String.join(", ", healthEndpoints)));
        }
    }
    
    /**
     * 测试Elasticsearch连接
     */
    private void testElasticsearchConnection(String url, String apiKey, String extraConfig, int timeout) {
        // 使用工具类解析extraConfig获取username和password
        String[] credentialsArray = vectorDatabaseConfigHelper.parseUsernamePasswordFromExtraConfig(extraConfig);
        String username = credentialsArray != null ? credentialsArray[0] : null;
        String password = credentialsArray != null ? credentialsArray[1] : null;
        
        // 配置认证：优先使用username/password（Basic Auth），其次使用API Key
        WebClient.Builder builder = KnowledgeBaseWebClientUtil.createBuilder(url);
        if (username != null && password != null && 
            !username.trim().isEmpty() && !password.trim().isEmpty()) {
            // 使用Basic Auth
            String credentials = username + ":" + password;
            String encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            builder.defaultHeader("Authorization", "Basic " + encodedCredentials);
            logger.debug("使用Basic Auth进行Elasticsearch连接测试 - 用户名: {}", username);
        } else if (apiKey != null && !apiKey.trim().isEmpty()) {
            // 使用API Key认证
            builder.defaultHeader("Authorization", "ApiKey " + apiKey);
            logger.debug("使用API Key进行Elasticsearch连接测试");
        }
        
        WebClient webClient = builder.build();
        
        // 尝试多个健康检查端点
        String[] healthEndpoints = {"/", "/_cluster/health", "/_cat/health"};
        String healthResponse = null;
        String successfulEndpoint = null;
        Exception lastException = null;
        
        for (String endpoint : healthEndpoints) {
            try {
                if (endpoint.equals("/_cluster/health") || endpoint.equals("/")) {
                    // 这些端点返回JSON
                    ParameterizedTypeReference<Map<String, Object>> typeRef = 
                            new ParameterizedTypeReference<Map<String, Object>>() {};
                    Map<String, Object> healthResponseMap = webClient
                            .get()
                            .uri(endpoint)
                            .retrieve()
                            .bodyToMono(typeRef)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                    
                    if (healthResponseMap != null) {
                        healthResponse = "OK";
                        successfulEndpoint = endpoint;
                        break;
                    }
                } else {
                    // 其他端点返回文本
                    healthResponse = webClient
                            .get()
                            .uri(endpoint)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                    
                    if (healthResponse != null && !healthResponse.trim().isEmpty()) {
                        successfulEndpoint = endpoint;
                        break;
                    }
                }
            } catch (WebClientResponseException e) {
                int statusCode = e.getStatusCode().value();
                // 如果是404，继续尝试下一个端点
                if (statusCode == 404) {
                    logger.debug("端点 {} 返回404，尝试下一个端点", endpoint);
                    lastException = e;
                    continue;
                }
                // 如果是401（认证失败），立即停止尝试，因为所有端点都需要认证
                if (statusCode == 401) {
                    logger.debug("端点 {} 返回401（认证失败），停止尝试其他端点", endpoint);
                    lastException = e;
                    break;
                }
                // 其他HTTP错误，记录并继续尝试
                logger.debug("端点 {} 返回错误 {}: {}", endpoint, e.getStatusCode(), e.getMessage());
                lastException = e;
            } catch (Exception e) {
                // 其他异常（如超时、网络错误等），记录并继续尝试
                logger.debug("端点 {} 访问异常: {}", endpoint, e.getMessage());
                lastException = e;
            }
        }
        
        // 如果找到成功的端点，返回
        if (successfulEndpoint != null && healthResponse != null) {
            return;
        }
        
        // 所有端点都失败
        if (lastException != null) {
            if (lastException instanceof WebClientResponseException) {
                WebClientResponseException webEx = (WebClientResponseException) lastException;
                int statusCode = webEx.getStatusCode().value();
                String errorMessage;
                
                if (statusCode == 401) {
                    // 401 Unauthorized - 认证失败
                    errorMessage = String.format(
                        "Elasticsearch连接失败: %d %s。已尝试端点: %s。\n" +
                        "认证失败，请检查：\n" +
                        "1. 如果Elasticsearch启用了安全功能（xpack.security.enabled=true），请提供用户名和密码（在extraConfig中）或API Key\n" +
                        "2. 用户名和密码格式：在extraConfig中提供JSON格式 {\"username\":\"your_username\",\"password\":\"your_password\"}\n" +
                        "3. 如果Elasticsearch未启用安全功能，请检查docker-compose.yml中的xpack.security.enabled设置\n" +
                        "4. 请确认URL、用户名、密码或API Key是否正确",
                        statusCode, 
                        webEx.getStatusText(),
                        String.join(", ", healthEndpoints));
                } else {
                    errorMessage = String.format(
                        "Elasticsearch连接失败: %d %s。已尝试端点: %s。请检查Elasticsearch服务是否正在运行，URL是否正确。", 
                        statusCode, 
                        webEx.getStatusText(),
                        String.join(", ", healthEndpoints));
                }
                
                throw new RuntimeException(errorMessage);
            } else {
                throw new RuntimeException(
                    String.format("Elasticsearch连接失败: %s。已尝试端点: %s。请检查Elasticsearch服务是否正在运行，URL是否正确。", 
                        lastException.getMessage(),
                        String.join(", ", healthEndpoints)));
            }
        } else {
            throw new RuntimeException(
                String.format("Elasticsearch健康检查返回空响应。已尝试端点: %s。请检查Elasticsearch服务是否正在运行。", 
                    String.join(", ", healthEndpoints)));
        }
    }
    
    /**
     * 测试PgVector连接
     */
    private void testPgVectorConnection(String url, String apiKey, String extraConfig, int timeout) {
        // 使用工具类解析extraConfig获取username和password
        String[] credentials = vectorDatabaseConfigHelper.parseUsernamePasswordFromExtraConfig(extraConfig);
        String username = credentials != null ? credentials[0] : null;
        String password = credentials != null ? credentials[1] : null;
        
        // 如果没有从extraConfig获取到用户名密码，尝试从apiKey字段获取（向后兼容）
        if (username == null && apiKey != null && !apiKey.trim().isEmpty()) {
            // apiKey 可能包含 "username:password" 格式
            if (apiKey.contains(":")) {
                String[] parts = apiKey.split(":", 2);
                username = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }
        }
        
        // 验证URL格式
        if (url == null || url.trim().isEmpty()) {
            throw new RuntimeException("PgVector URL 不能为空，请配置有效的 JDBC URL（例如：jdbc:postgresql://localhost:5432/vectordb）");
        }
        
        String jdbcUrl = url.trim();
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            // 如果不是完整的JDBC URL，尝试自动转换
            if (jdbcUrl.startsWith("http://") || jdbcUrl.startsWith("https://")) {
                throw new RuntimeException(
                    String.format("PgVector URL 必须是 JDBC 格式，当前配置为: %s。请配置为 JDBC URL（例如：jdbc:postgresql://localhost:5432/vectordb）", 
                        jdbcUrl));
            } else if (jdbcUrl.contains("://")) {
                // 可能是 postgresql://host:port/db 格式，转换为 jdbc:postgresql://host:port/db
                jdbcUrl = jdbcUrl.replaceFirst("^postgresql://", "jdbc:postgresql://");
            } else {
                // 假设是 host:port/db 格式
                jdbcUrl = "jdbc:postgresql://" + jdbcUrl;
            }
        }
        
        // 验证用户名和密码
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("PgVector 需要用户名，请在 extraConfig 中配置 {\"username\":\"your_username\",\"password\":\"your_password\"}");
        }
        
        // 测试数据库连接
        java.sql.Connection conn = null;
        try {
            // 加载PostgreSQL驱动
            Class.forName("org.postgresql.Driver");
            
            // 设置连接超时
            java.util.Properties props = new java.util.Properties();
            props.setProperty("user", username);
            if (password != null) {
                props.setProperty("password", password);
            }
            props.setProperty("connectTimeout", String.valueOf(timeout / 1000)); // PostgreSQL超时单位是秒
            
            // 建立连接
            conn = java.sql.DriverManager.getConnection(jdbcUrl, props);
            
            // 检查pgvector扩展是否已安装
            try (java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')")) {
                
                if (rs.next() && rs.getBoolean(1)) {
                    logger.debug("PgVector连接测试成功，pgvector扩展已安装");
                } else {
                    throw new RuntimeException(
                        "PostgreSQL连接成功，但pgvector扩展未安装。请执行以下SQL安装扩展：\n" +
                        "CREATE EXTENSION IF NOT EXISTS vector;");
                }
            }
            
        } catch (java.sql.SQLException e) {
            String errorMessage = e.getMessage();
            if (errorMessage.contains("password authentication failed")) {
                throw new RuntimeException(
                    "PgVector连接失败: 用户名或密码错误。请检查extraConfig中的用户名和密码配置。");
            } else if (errorMessage.contains("Connection refused") || errorMessage.contains("timeout")) {
                throw new RuntimeException(
                    String.format("PgVector连接失败: 无法连接到PostgreSQL服务器。请检查URL是否正确，服务器是否正在运行。URL: %s", jdbcUrl));
            } else if (errorMessage.contains("database") && errorMessage.contains("does not exist")) {
                throw new RuntimeException(
                    "PgVector连接失败: 指定的数据库不存在。请检查URL中的数据库名称是否正确。");
            } else {
                throw new RuntimeException(
                    String.format("PgVector连接失败: %s。请检查URL、用户名和密码是否正确。", errorMessage));
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL驱动未找到，请确保已添加postgresql依赖");
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (java.sql.SQLException e) {
                    logger.debug("关闭连接时出错: {}", e.getMessage());
                }
            }
        }
    }
    
}