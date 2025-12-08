package com.github.app.dify.langchain4j;

import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.service.VectorStoreStrategy;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 向量存储工厂类
 * 根据知识库的vector_store_type配置选择创建Qdrant、FAISS、Milvus、Chroma或Weaviate EmbeddingStore
 */
@Component
public class VectorStoreFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreFactory.class);
    
    @Autowired(required = false)
    private java.util.List<VectorStoreStrategy> strategies;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
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
        if ("faiss".equals(strategyType)) {
            return FaissEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
        } else if ("milvus".equals(strategyType)) {
            return MilvusEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
        } else if ("chroma".equals(strategyType)) {
            return ChromaEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
        } else if ("weaviate".equals(strategyType)) {
            return WeaviateEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
        } else {
            // 默认使用Qdrant
            return QdrantEmbeddingStore.forKnowledgeBase(knowledgeBaseId, strategy);
        }
    }
    
    /**
     * 获取知识库的向量存储类型
     * @param knowledgeBaseId 知识库ID
     * @return 向量存储类型（qdrant、faiss、milvus、chroma、weaviate），默认为qdrant
     */
    private String getVectorStoreType(Long knowledgeBaseId) {
        try {
            return knowledgeBaseRepository.findById(knowledgeBaseId)
                    .map(kb -> {
                        String type = kb.getVectorStoreType();
                        return type != null && !type.trim().isEmpty() ? type : "qdrant";
                    })
                    .orElse("qdrant");
        } catch (Exception e) {
            logger.warn("获取知识库向量存储类型失败，使用默认值qdrant - 知识库ID: {}", knowledgeBaseId, e);
            return "qdrant";
        }
    }
}

