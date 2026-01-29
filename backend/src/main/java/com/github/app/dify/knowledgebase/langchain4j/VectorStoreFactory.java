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

import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量存储工厂类
 * 
 * <p>负责创建和管理向量数据库的EmbeddingStore实例。根据知识库配置的向量存储类型，
 * 自动选择对应的策略实现并创建相应的EmbeddingStore实例。
 * 
 * <p>支持的向量数据库：
 * <ul>
 *   <li>Qdrant - 默认向量库，高性能向量搜索引擎</li>
 *   <li>FAISS - Facebook AI Similarity Search</li>
 *   <li>Milvus - 开源向量数据库</li>
 *   <li>Chroma - 轻量级向量数据库</li>
 *   <li>Weaviate - 开源向量搜索引擎</li>
 *   <li>PgVector - 基于PostgreSQL的向量扩展</li>
 *   <li>Elasticsearch - 支持向量搜索的搜索引擎</li>
 * </ul>
 * 
 * <p>工作原理：
 * <ol>
 *   <li>从知识库配置获取向量存储类型（优先从vectorDatabaseId获取实例配置）</li>
 *   <li>根据类型从策略映射中获取对应的{@link VectorStoreStrategy}</li>
 *   <li>使用策略创建对应的EmbeddingStore实例（langchain4j.store包中的实现）</li>
 *   <li>缓存EmbeddingStore实例，避免重复创建</li>
 * </ol>
 * 
 * <p>缓存机制：
 * <ul>
 *   <li>使用ConcurrentHashMap缓存EmbeddingStore实例</li>
 *   <li>以knowledgeBaseId为键，支持多知识库隔离</li>
 *   <li>文档解读使用固定的knowledgeBaseId（0L）</li>
 * </ul>
 * 
 * <p>策略模式：
 * 通过{@link VectorStoreStrategy}接口实现策略模式，支持灵活扩展新的向量数据库。
 * 系统会自动发现所有注册的VectorStoreStrategy实现，并建立类型到策略的映射。
 * 
 * <p>配置优先级：
 * <ol>
 *   <li>vectorDatabaseId指向的向量库实例配置（最高优先级）</li>
 *   <li>知识库的vectorStoreType配置</li>
 *   <li>默认值（知识库：qdrant，文档解读：pgvector）</li>
 * </ol>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>知识库文档向量化后的存储</li>
 *   <li>RAG检索时的向量查询</li>
 *   <li>文档解读模块的向量存储</li>
 * </ul>
 * 
 * @see VectorStoreStrategy
 * @see EmbeddingStore
 * @see com.github.app.dify.knowledgebase.langchain4j.store
 * @author DifyApp Team
 * @since 1.0
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
    
    // EmbeddingStore实例缓存：knowledgeBaseId -> EmbeddingStore
    private final ConcurrentHashMap<Long, EmbeddingStore<TextSegment>> embeddingStoreCache =
            new ConcurrentHashMap<>();
    
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
     * 为指定知识库创建EmbeddingStore（带缓存）
     * @param knowledgeBaseId 知识库ID
     * @return EmbeddingStore实例
     */
    public EmbeddingStore<TextSegment> createEmbeddingStore(Long knowledgeBaseId) {
        // 优先从缓存获取
        EmbeddingStore<TextSegment> cachedStore = embeddingStoreCache.get(knowledgeBaseId);
        if (cachedStore != null) {
            logger.debug("从缓存获取EmbeddingStore - 知识库ID: {}", knowledgeBaseId);
            return cachedStore;
        }
        
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
        EmbeddingStore<TextSegment> embeddingStore = switch (strategyType) {
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
        
        // 存入缓存（使用putIfAbsent避免并发问题）
        EmbeddingStore<TextSegment> existingStore = embeddingStoreCache.putIfAbsent(knowledgeBaseId, embeddingStore);
        if (existingStore != null) {
            logger.debug("EmbeddingStore已被其他线程创建，使用缓存实例 - 知识库ID: {}", knowledgeBaseId);
            return existingStore;
        }
        
        logger.info("EmbeddingStore已创建并缓存 - 知识库ID: {}, 类型: {}, 当前缓存数量: {}", 
                knowledgeBaseId, vectorStoreType, embeddingStoreCache.size());
        return embeddingStore;
    }

    /**
     * 使指定知识库的 EmbeddingStore 缓存失效（知识库向量配置变更时调用，避免使用旧连接/类型）
     * @param knowledgeBaseId 知识库ID
     */
    public void invalidateEmbeddingStore(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return;
        }
        EmbeddingStore<TextSegment> removed = embeddingStoreCache.remove(knowledgeBaseId);
        if (removed != null) {
            logger.info("已使 EmbeddingStore 缓存失效 - 知识库ID: {}, 当前缓存数量: {}", 
                    knowledgeBaseId, embeddingStoreCache.size());
        }
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
                    Long vectorDatabaseId = knowledgeBase.getVectorDatabaseId();
                    if (vectorDatabaseId != null && vectorDatabaseRepository != null) {
                        java.util.Optional<com.github.app.dify.knowledgebase.domain.VectorDatabase> config = 
                                vectorDatabaseRepository.findById(vectorDatabaseId);
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
