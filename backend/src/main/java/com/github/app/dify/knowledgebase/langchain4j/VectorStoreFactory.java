package com.github.app.dify.knowledgebase.langchain4j;

import com.github.app.dify.knowledgebase.langchain4j.store.*;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;
import com.github.app.dify.system.config.DocumentReaderConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
/**
 * 向量存储工厂类
 * 根据知识库的vector_store_type配置选择创建Qdrant、FAISS、Milvus、Chroma、Weaviate、Elasticsearch或PgVector EmbeddingStore
 */
@Component
public class VectorStoreFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreFactory.class);
    
    @Autowired(required = false)
    private java.util.List<VectorStoreStrategy> strategies;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired(required = false)
    private DocumentReaderConfig documentReaderConfig;
    
    @Autowired(required = false)
    private com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository vectorDatabaseRepository;
    
    // 策略缓存：类型 -> 策略实例
    private java.util.Map<String, VectorStoreStrategy> strategyMap;
    
    /**
     * 初始化策略映射
     */
    private void initStrategyMap() {
        if (strategyMap == null && strategies != null) {
            strategyMap = strategies.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            VectorStoreStrategy::getType,
                            strategy -> strategy,
                            (existing, replacement) -> existing
                    ));
            logger.info("初始化向量存储策略映射 - 策略数量: {}, 类型: {}", 
                    strategyMap.size(), strategyMap.keySet());
        }
    }
    
    /**
     * 根据类型获取策略
     */
    private VectorStoreStrategy getStrategy(String type) {
        initStrategyMap();
        if (strategyMap == null) {
            return null;
        }
        return strategyMap.get(type.toLowerCase());
    }
    
    /**
     * 为指定知识库创建EmbeddingStore
     * @param knowledgeBaseId 知识库ID
     * @return EmbeddingStore实例
     */
    public EmbeddingStore<TextSegment> createEmbeddingStore(Long knowledgeBaseId) {
        // 获取知识库的向量存储类型
        String vectorStoreType = getVectorStoreType(knowledgeBaseId);
        
        logger.debug("为知识库创建EmbeddingStore - 知识库ID: {}, 向量存储类型: {}", 
                knowledgeBaseId, vectorStoreType);
        
        VectorStoreStrategy strategy = getStrategy(vectorStoreType);
        if (strategy == null) {
            // 如果没有找到策略，尝试使用默认策略（qdrant）
            strategy = getStrategy("qdrant");
            if (strategy == null && strategyMap != null && !strategyMap.isEmpty()) {
                // 如果qdrant也没有，使用第一个可用的策略
                strategy = strategyMap.values().iterator().next();
            }
            logger.warn("未找到向量存储策略，使用默认策略 - 知识库ID: {}, 类型: {}, 使用策略: {}", 
                    knowledgeBaseId, vectorStoreType, strategy != null ? strategy.getType() : "none");
        }
        
        if (strategy == null) {
            throw new IllegalStateException("没有可用的向量存储策略 - 知识库ID: " + knowledgeBaseId);
        }
        
        // 根据策略类型创建对应的EmbeddingStore
        String strategyType = strategy.getType().toLowerCase();
        return switch (strategyType) {
            case "faiss" -> FaissEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
            case "milvus" -> MilvusEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
            case "chroma" -> ChromaEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
            case "weaviate" -> WeaviateEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
            case "elasticsearch" ->
                // Elasticsearch使用QdrantEmbeddingStore的相同接口（因为它们都实现了VectorStoreStrategy）
                    QdrantEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
            case "pgvector" -> PgVectorEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
            default ->
                // 默认使用Qdrant
                    QdrantEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
        };
    }
    
    /**
     * 获取知识库的向量存储类型
     * 优先从vectorDatabaseId获取具体实例配置，然后从实例配置中读取类型
     * 如果没有vectorDatabaseId，才使用vectorStoreType配置
     * @param knowledgeBaseId 知识库ID
     * @return 向量存储类型（qdrant、faiss、milvus、chroma、weaviate、elasticsearch、pgvector），文档解读默认为pgvector，知识库默认为qdrant
     */
    private String getVectorStoreType(Long knowledgeBaseId) {
        try {
            // 如果是文档解读（knowledgeBaseId为0），优先从DocumentReaderConfig读取vectorDatabaseId
            if (knowledgeBaseId != null && knowledgeBaseId == 0L && documentReaderConfig != null) {
                // 优先从vectorDatabaseId获取实例配置
                Long vectorDatabaseId = documentReaderConfig.getVectorDatabaseId();
                if (vectorDatabaseId != null && vectorDatabaseRepository != null) {
                    java.util.Optional<com.github.app.dify.knowledgebase.domain.VectorDatabase> config = 
                            vectorDatabaseRepository.findById(vectorDatabaseId);
                    if (config.isPresent()) {
                        String type = config.get().getType();
                        if (type != null && !type.trim().isEmpty()) {
                            logger.debug("从文档解读配置的向量库实例读取类型 - 实例ID: {}, 类型: {}", 
                                    vectorDatabaseId, type);
                            return type.toLowerCase();
                        }
                    } else {
                        logger.warn("文档解读配置的向量库实例ID不存在: {}, 使用类型配置", vectorDatabaseId);
                    }
                }
                
                // 如果没有vectorDatabaseId或实例不存在，使用vectorStoreType配置
                String type = documentReaderConfig.getVectorStoreType();
                if (type != null && !type.trim().isEmpty()) {
                    logger.debug("从文档解读配置读取向量存储类型: {}", type);
                    return type.toLowerCase();
                }
                // 如果文档解读配置中没有类型，使用DocumentReaderConfig的默认值（pgvector）
                // 注意：DocumentReaderConfig在初始化时会设置默认值，所以这里理论上不会执行
                logger.warn("文档解读配置中向量存储类型为空，使用DocumentReaderConfig默认值pgvector");
                return "pgvector";
            }
            
            // 对于知识库，优先从vectorDatabaseId获取实例配置
            if (knowledgeBaseRepository != null) {
                assert knowledgeBaseId != null;
                java.util.Optional<com.github.app.dify.knowledgebase.domain.KnowledgeBase> kb =
                        knowledgeBaseRepository.findById(knowledgeBaseId);
                if (kb.isPresent()) {
                    com.github.app.dify.knowledgebase.domain.KnowledgeBase knowledgeBase = kb.get();
                    
                    // 优先从vectorDatabaseId获取实例配置
                    if (knowledgeBase.getVectorDatabaseId() != null && vectorDatabaseRepository != null) {
                        java.util.Optional<com.github.app.dify.knowledgebase.domain.VectorDatabase> config = 
                                vectorDatabaseRepository.findById(knowledgeBase.getVectorDatabaseId());
                        if (config.isPresent()) {
                            String type = config.get().getType();
                            if (type != null && !type.trim().isEmpty()) {
                                logger.debug("从知识库的向量库实例读取类型 - 知识库ID: {}, 实例ID: {}, 类型: {}", 
                                        knowledgeBaseId, knowledgeBase.getVectorDatabaseId(), type);
                                return type.toLowerCase();
                            }
                        }
                    }
                    
                    // 如果没有vectorDatabaseId或实例不存在，使用vectorStoreType配置
                    String type = knowledgeBase.getVectorStoreType();
                    if (type != null && !type.trim().isEmpty()) {
                        logger.debug("从知识库配置读取向量存储类型 - 知识库ID: {}, 类型: {}", 
                                knowledgeBaseId, type);
                        return type.toLowerCase();
                    }
                }
            }
        } catch (Exception e) {
            // 如果是文档解读，使用pgvector作为默认值；否则使用qdrant
            if (knowledgeBaseId == 0L) {
                logger.warn("获取文档解读向量存储类型失败，使用默认值pgvector - 知识库ID: {}", knowledgeBaseId, e);
                return "pgvector";
            } else {
                logger.warn("获取知识库向量存储类型失败，使用默认值qdrant - 知识库ID: {}", knowledgeBaseId, e);
            }
        }
        
        // 默认返回qdrant（仅用于知识库，文档解读应该在上面已经返回）
        logger.debug("使用默认向量存储类型qdrant - 知识库ID: {}", knowledgeBaseId);
        return "qdrant";
    }
    
    /**
     * 为文档解读创建EmbeddingStore（使用文档解读配置）
     * @return EmbeddingStore实例
     */
    public EmbeddingStore<TextSegment> createDocumentReaderEmbeddingStore() {
        // 文档解读使用固定的知识库ID（0）来标识
        // 向量存储类型从DocumentReaderConfig读取
        return createEmbeddingStore(0L);
    }
}