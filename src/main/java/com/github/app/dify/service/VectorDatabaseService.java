package com.github.app.dify.service;

import com.github.app.dify.config.FaissConfig;
import com.github.app.dify.config.MilvusConfig;
import com.github.app.dify.config.QdrantConfig;
import com.github.app.dify.domain.VectorDatabase;
import com.github.app.dify.repository.VectorDatabaseRepository;
import com.github.app.dify.req.TestVectorDatabaseConnectionRequest;
import com.github.app.dify.req.VectorDatabaseRequest;
import com.github.app.dify.resp.VectorDatabaseResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 向量数据库配置服务
 */
@Service
public class VectorDatabaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseService.class);
    
    @Autowired
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired(required = false)
    private QdrantConfig qdrantConfig;
    
    @Autowired(required = false)
    private MilvusConfig milvusConfig;
    
    @Autowired(required = false)
    private FaissConfig faissConfig;
    
    
    /**
     * 获取所有向量数据库配置
     */
    @Cacheable(value = "vectorDatabase", key = "'all'")
    public List<VectorDatabaseResp> getAllConfigs() {
        List<VectorDatabase> configs = vectorDatabaseRepository.findAllActive();
        return configs.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据类型获取配置列表
     */
    public List<VectorDatabaseResp> getConfigsByType(String type) {
        List<VectorDatabase> configs = vectorDatabaseRepository.findByType(type);
        return configs.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取默认配置（按类型）
     */
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
    
    /**
     * 更新配置
     */
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
        config.setDescription(info.getDescription());
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
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
        
        return convertToResp(saved);
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
        config.setUpdateTime(new Date());
        
        VectorDatabase saved = vectorDatabaseRepository.save(config);
        logger.info("更新向量数据库配置成功 - ID: {}, 名称: {}, 类型: {}", 
                saved.getId(), saved.getName(), saved.getType());
        
        // 如果更新的是默认配置，重新加载配置
        if (saved.getIsDefault() != null && saved.getIsDefault() && saved.getEnabled()) {
            reloadConfig(saved.getType());
        }
        
        return convertToResp(saved);
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
        config.setDeleted(1);
        config.setUpdateTime(new Date());
        vectorDatabaseRepository.save(config);
        
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
        
        // 取消同类型其他配置的默认状态
        List<VectorDatabase> sameTypeConfigs = vectorDatabaseRepository.findByType(config.getType());
        for (VectorDatabase c : sameTypeConfigs) {
            if (c.getIsDefault() != null && c.getIsDefault() && !c.getId().equals(configId)) {
                c.setIsDefault(false);
                c.setUpdateTime(new Date());
                vectorDatabaseRepository.save(c);
            }
        }
        
        // 设置当前配置为默认
        config.setIsDefault(true);
        config.setUpdateTime(new Date());
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
        config.setUpdateTime(new Date());
        
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
            }
        } catch (Exception e) {
            logger.warn("重新加载{}配置失败: {}", type, e.getMessage());
        }
    }
    
    /**
     * 测试连接
     */
    public void testConnection(TestVectorDatabaseConnectionRequest request) {
        String type = request.getType();
        String url = request.getUrl();
        String apiKey = request.getApiKey();
        Integer timeout = request.getTimeout() != null ? request.getTimeout() : 30000;
        
        try {
            if ("qdrant".equalsIgnoreCase(type)) {
                testQdrantConnection(url, apiKey, timeout);
            } else if ("milvus".equalsIgnoreCase(type)) {
                testMilvusConnection(url, apiKey, timeout);
            } else if ("faiss".equalsIgnoreCase(type)) {
                testFaissConnection(url);
            } else {
                throw new RuntimeException("不支持的数据库类型: " + type);
            }
        } catch (Exception e) {
            logger.error("测试向量数据库连接失败 - 类型: {}, URL: {}", type, url, e);
            throw new RuntimeException("测试连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试Qdrant连接
     */
    private void testQdrantConnection(String url, String apiKey, int timeout) {
        // 使用QdrantConfig的测试方法
        org.springframework.web.reactive.function.client.WebClient.Builder builder = 
                org.springframework.web.reactive.function.client.WebClient.builder()
                        .baseUrl(url)
                        .defaultHeader(org.springframework.http.HttpHeaders.CONTENT_TYPE, 
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        
        org.springframework.web.reactive.function.client.WebClient webClient = builder.build();
        
        String healthResponse = webClient
                .get()
                .uri("/healthz")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofMillis(timeout))
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
        
        org.springframework.web.reactive.function.client.WebClient.Builder builder = 
                org.springframework.web.reactive.function.client.WebClient.builder()
                        .baseUrl(trimmedUrl)
                        .defaultHeader(org.springframework.http.HttpHeaders.CONTENT_TYPE, 
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        
        org.springframework.web.reactive.function.client.WebClient webClient = builder.build();
        
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
                        .timeout(java.time.Duration.ofMillis(timeout))
                        .block();
                
                // 如果成功获取响应且不为空，则连接成功
                if (healthResponse != null && !healthResponse.trim().isEmpty()) {
                    successfulEndpoint = endpoint;
                    logger.debug("Milvus健康检查成功，使用的端点: {} (URL: {})", endpoint, trimmedUrl);
                    break;
                }
            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
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
                java.net.URL urlObj = new java.net.URL(trimmedUrl);
                String metricsUrl = urlObj.getProtocol() + "://" + urlObj.getHost() + ":9091";
                logger.debug("19530端口端点都失败，尝试9091端口（metrics端口）的健康检查: {}", metricsUrl);
                
                org.springframework.web.reactive.function.client.WebClient.Builder metricsBuilder = 
                        org.springframework.web.reactive.function.client.WebClient.builder()
                                .baseUrl(metricsUrl)
                                .defaultHeader(org.springframework.http.HttpHeaders.CONTENT_TYPE, 
                                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    metricsBuilder.defaultHeader(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                }
                
                org.springframework.web.reactive.function.client.WebClient metricsWebClient = metricsBuilder.build();
                
                try {
                    healthResponse = metricsWebClient
                            .get()
                            .uri("/healthz")
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(java.time.Duration.ofMillis(timeout))
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
            if (lastException instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                org.springframework.web.reactive.function.client.WebClientResponseException webEx = 
                    (org.springframework.web.reactive.function.client.WebClientResponseException) lastException;
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
     * 测试FAISS连接（检查路径是否存在）
     */
    private void testFaissConnection(String path) {
        java.io.File dir = new java.io.File(path);
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
     * 转换为响应对象
     */
    private VectorDatabaseResp convertToResp(VectorDatabase config) {
        VectorDatabaseResp resp = new VectorDatabaseResp();
        BeanUtils.copyProperties(config, resp);
        // 隐藏敏感信息（API Key只显示前4位和后4位）
        if (config.getApiKey() != null && config.getApiKey().length() > 8) {
            String apiKey = config.getApiKey();
            resp.setApiKey(apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
        } else if (config.getApiKey() != null) {
            resp.setApiKey("****");
        }
        return resp;
    }
}

