package com.github.app.dify.system.config;

import com.github.app.dify.system.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 文档解读配置
 * 从系统配置表读取配置，如果系统配置表中没有，则使用默认值
 */
@Component
public class DocumentReaderConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderConfig.class);
    
    // 配置键
    private static final String CONFIG_KEY_DEFAULT_QA_MODEL_ID = "documentReader.defaultQAModelId";
    private static final String CONFIG_KEY_DEFAULT_EMBEDDING_MODEL_ID = "documentReader.defaultEmbeddingModelId";
    private static final String CONFIG_KEY_VECTOR_STORE_TYPE = "documentReader.vectorStoreType";
    private static final String CONFIG_KEY_VECTOR_DATABASE_ID = "documentReader.vectorDatabaseId";
    private static final String CONFIG_KEY_TOP_K = "documentReader.topK";
    
    // 默认值
    private static final String DEFAULT_VECTOR_STORE_TYPE = "qdrant";
    private static final Integer DEFAULT_TOP_K = 5;
    
    @Autowired(required = false)
    private SystemConfigService systemConfigService;
    
    // 配置值
    private Long defaultQAModelId;
    private Long defaultEmbeddingModelId;
    private String vectorStoreType;
    private Long vectorDatabaseId;
    private Integer topK;
    
    @PostConstruct
    public void init() {
        if (systemConfigService != null) {
            loadConfigFromSystemConfig();
        } else {
            logger.warn("SystemConfigService 未注入，使用默认文档解读配置");
            useDefaultConfig();
        }
    }
    
    /**
     * 从系统配置表加载配置
     */
    private void loadConfigFromSystemConfig() {
        try {
            // 加载默认问答模型ID
            String qaModelIdStr = systemConfigService.getConfigValue(CONFIG_KEY_DEFAULT_QA_MODEL_ID);
            if (qaModelIdStr != null && !qaModelIdStr.trim().isEmpty()) {
                try {
                    this.defaultQAModelId = Long.parseLong(qaModelIdStr.trim());
                    logger.info("从系统配置加载文档解读默认问答模型ID: {}", this.defaultQAModelId);
                } catch (NumberFormatException e) {
                    logger.warn("系统配置中的文档解读默认问答模型ID格式错误: {}, 使用null", qaModelIdStr);
                }
            }
            
            // 加载默认向量化模型ID
            String embeddingModelIdStr = systemConfigService.getConfigValue(CONFIG_KEY_DEFAULT_EMBEDDING_MODEL_ID);
            if (embeddingModelIdStr != null && !embeddingModelIdStr.trim().isEmpty()) {
                try {
                    this.defaultEmbeddingModelId = Long.parseLong(embeddingModelIdStr.trim());
                    logger.info("从系统配置加载文档解读默认向量化模型ID: {}", this.defaultEmbeddingModelId);
                } catch (NumberFormatException e) {
                    logger.warn("系统配置中的文档解读默认向量化模型ID格式错误: {}, 使用null", embeddingModelIdStr);
                }
            }
            
            // 加载向量库类型
            String vectorStoreTypeStr = systemConfigService.getConfigValue(CONFIG_KEY_VECTOR_STORE_TYPE);
            if (vectorStoreTypeStr != null && !vectorStoreTypeStr.trim().isEmpty()) {
                this.vectorStoreType = vectorStoreTypeStr.trim().toLowerCase();
                logger.info("从系统配置加载文档解读向量库类型: {}", this.vectorStoreType);
            } else {
                this.vectorStoreType = DEFAULT_VECTOR_STORE_TYPE;
                logger.info("使用默认文档解读向量库类型: {}", this.vectorStoreType);
            }
            
            // 加载向量库实例ID
            String vectorDatabaseIdStr = systemConfigService.getConfigValue(CONFIG_KEY_VECTOR_DATABASE_ID);
            if (vectorDatabaseIdStr != null && !vectorDatabaseIdStr.trim().isEmpty()) {
                try {
                    this.vectorDatabaseId = Long.parseLong(vectorDatabaseIdStr.trim());
                    logger.info("从系统配置加载文档解读向量库实例ID: {}", this.vectorDatabaseId);
                } catch (NumberFormatException e) {
                    logger.warn("系统配置中的文档解读向量库实例ID格式错误: {}, 使用null", vectorDatabaseIdStr);
                }
            }
            
            // 加载Top-K
            String topKStr = systemConfigService.getConfigValue(CONFIG_KEY_TOP_K);
            if (topKStr != null && !topKStr.trim().isEmpty()) {
                try {
                    this.topK = Integer.parseInt(topKStr.trim());
                    logger.info("从系统配置加载文档解读Top-K: {}", this.topK);
                } catch (NumberFormatException e) {
                    logger.warn("系统配置中的文档解读Top-K格式错误: {}, 使用默认值: {}", topKStr, DEFAULT_TOP_K);
                    this.topK = DEFAULT_TOP_K;
                }
            } else {
                this.topK = DEFAULT_TOP_K;
                logger.info("使用默认文档解读Top-K: {}", this.topK);
            }
            
        } catch (Exception e) {
            logger.error("从系统配置加载文档解读配置失败，使用默认值", e);
            useDefaultConfig();
        }
    }
    
    /**
     * 使用默认配置
     */
    private void useDefaultConfig() {
        this.defaultQAModelId = null;
        this.defaultEmbeddingModelId = null;
        this.vectorStoreType = DEFAULT_VECTOR_STORE_TYPE;
        this.vectorDatabaseId = null;
        this.topK = DEFAULT_TOP_K;
    }
    
    /**
     * 重新加载配置（当配置更新时调用）
     */
    public void reload() {
        if (systemConfigService != null) {
            loadConfigFromSystemConfig();
        } else {
            useDefaultConfig();
        }
    }
    
    // Getters
    public Long getDefaultQAModelId() {
        return defaultQAModelId;
    }
    
    public Long getDefaultEmbeddingModelId() {
        return defaultEmbeddingModelId;
    }
    
    public String getVectorStoreType() {
        return vectorStoreType;
    }
    
    public Long getVectorDatabaseId() {
        return vectorDatabaseId;
    }
    
    public Integer getTopK() {
        return topK;
    }
    
    // 配置键常量（供外部使用）
    public static String getConfigKeyDefaultQAModelId() {
        return CONFIG_KEY_DEFAULT_QA_MODEL_ID;
    }
    
    public static String getConfigKeyDefaultEmbeddingModelId() {
        return CONFIG_KEY_DEFAULT_EMBEDDING_MODEL_ID;
    }
    
    public static String getConfigKeyVectorStoreType() {
        return CONFIG_KEY_VECTOR_STORE_TYPE;
    }
    
    public static String getConfigKeyVectorDatabaseId() {
        return CONFIG_KEY_VECTOR_DATABASE_ID;
    }
    
    public static String getConfigKeyTopK() {
        return CONFIG_KEY_TOP_K;
    }
}

