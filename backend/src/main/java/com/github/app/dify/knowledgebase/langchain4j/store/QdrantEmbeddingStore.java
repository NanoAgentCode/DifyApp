package com.github.app.dify.knowledgebase.langchain4j.store;

import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;

/**
 * Qdrant EmbeddingStore实现，适配Qdrant存储
 * 支持按知识库ID隔离存储（每个知识库一个集合）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class QdrantEmbeddingStore extends BaseEmbeddingStore {

    public static QdrantEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, VectorStoreStrategy strategy) {
        QdrantEmbeddingStore store = new QdrantEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
}
