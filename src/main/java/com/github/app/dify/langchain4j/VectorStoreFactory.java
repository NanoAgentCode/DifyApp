package com.github.app.dify.langchain4j;

import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.service.ChromaVectorStoreService;
import com.github.app.dify.service.FaissVectorStoreService;
import com.github.app.dify.service.MilvusVectorStoreService;
import com.github.app.dify.service.VectorStoreService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 向量存储工厂类
 * 根据知识库的vector_store_type配置选择创建Qdrant、FAISS、Milvus或Chroma EmbeddingStore
 */
@Component
public class VectorStoreFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreFactory.class);
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private FaissVectorStoreService faissVectorStoreService;
    
    @Autowired
    private MilvusVectorStoreService milvusVectorStoreService;
    
    @Autowired
    private ChromaVectorStoreService chromaVectorStoreService;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
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
        
        if ("faiss".equalsIgnoreCase(vectorStoreType)) {
            return FaissEmbeddingStore.forKnowledgeBase(knowledgeBaseId, faissVectorStoreService);
        } else if ("milvus".equalsIgnoreCase(vectorStoreType)) {
            return MilvusEmbeddingStore.forKnowledgeBase(knowledgeBaseId, milvusVectorStoreService);
        } else if ("chroma".equalsIgnoreCase(vectorStoreType)) {
            return ChromaEmbeddingStore.forKnowledgeBase(knowledgeBaseId, chromaVectorStoreService);
        } else {
            // 默认使用Qdrant
            return QdrantEmbeddingStore.forKnowledgeBase(knowledgeBaseId, vectorStoreService);
        }
    }
    
    /**
     * 获取知识库的向量存储类型
     * @param knowledgeBaseId 知识库ID
     * @return 向量存储类型（qdrant、faiss、milvus、chroma），默认为qdrant
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

