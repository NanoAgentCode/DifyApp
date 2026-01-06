package com.github.app.dify.knowledgebase.langchain4j.store;

import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;

/**
 * Chroma EmbeddingStore实现，适配Chroma存储
 * 支持按知识库ID隔离存储（每个知识库一个集合）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class ChromaEmbeddingStore extends BaseEmbeddingStore {

    public static ChromaEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, 
                                                        VectorStoreStrategy strategy) {
        ChromaEmbeddingStore store = new ChromaEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
}
