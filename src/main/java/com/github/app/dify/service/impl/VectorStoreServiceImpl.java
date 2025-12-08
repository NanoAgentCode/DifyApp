package com.github.app.dify.service.impl;

import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.service.VectorStoreService;
import com.github.app.dify.service.VectorStoreStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * 向量存储服务实现
 * 作为策略模式的上下文，根据知识库配置选择合适的向量存储策略
 */
@Service
public class VectorStoreServiceImpl implements VectorStoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreServiceImpl.class);
    
    @Autowired(required = false)
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    // 注入所有策略实现
    @Autowired(required = false)
    private List<VectorStoreStrategy> strategies;
    
    // 策略缓存：类型 -> 策略实例
    private Map<String, VectorStoreStrategy> strategyMap;
    
    /**
     * 初始化策略映射
     */
    private void initStrategyMap() {
        if (strategyMap == null && strategies != null) {
            strategyMap = strategies.stream()
                    .collect(Collectors.toMap(
                            VectorStoreStrategy::getType,
                            strategy -> strategy,
                            (existing, replacement) -> existing
                    ));
            logger.info("初始化向量存储策略映射 - 策略数量: {}, 类型: {}", 
                    strategyMap.size(), strategyMap.keySet());
        }
    }
    
    /**
     * 根据知识库ID获取向量存储策略
     */
    private VectorStoreStrategy getStrategy(Long knowledgeBaseId) {
        initStrategyMap();
        
        String vectorStoreType = getVectorStoreType(knowledgeBaseId);
        VectorStoreStrategy strategy = strategyMap != null ? strategyMap.get(vectorStoreType.toLowerCase()) : null;
        
        if (strategy == null) {
            // 如果没有找到策略，尝试使用默认策略（qdrant）
            strategy = strategyMap != null ? strategyMap.get("qdrant") : null;
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
        
        return strategy;
    }
    
    /**
     * 获取知识库的向量存储类型
     */
    private String getVectorStoreType(Long knowledgeBaseId) {
        if (knowledgeBaseRepository == null) {
            return "qdrant"; // 默认
        }
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
    
    @Override
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        VectorStoreStrategy strategy = getStrategy(knowledgeBaseId);
        strategy.ensureCollection(knowledgeBaseId, vectorSize);
    }
    
    @Override
    public void upsertVectors(Long knowledgeBaseId, Long documentId, 
                              List<List<Float>> vectors, List<String> texts, 
                              List<Integer> chunkIndices) {
        VectorStoreStrategy strategy = getStrategy(knowledgeBaseId);
        strategy.upsertVectors(knowledgeBaseId, documentId, vectors, texts, chunkIndices);
    }
    
    @Override
    public List<VectorStoreStrategy.SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK) {
        VectorStoreStrategy strategy = getStrategy(knowledgeBaseId);
        return strategy.searchVectors(knowledgeBaseId, queryVector, topK);
    }
    
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        VectorStoreStrategy strategy = getStrategy(knowledgeBaseId);
        strategy.deleteDocumentVectors(knowledgeBaseId, documentId);
    }
    
    @Override
    public String getCollectionName(Long knowledgeBaseId) {
        return "kb_" + knowledgeBaseId;
    }
}