package com.github.app.dify.knowledgebase.langchain4j.store;

import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;

/**
 * PgVector EmbeddingStore实现，适配PgVector存储
 * 支持按知识库ID隔离存储（每个知识库一个表）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class PgVectorEmbeddingStore extends BaseEmbeddingStore {

    public static PgVectorEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, VectorStoreStrategy strategy) {
        PgVectorEmbeddingStore store = new PgVectorEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
}
