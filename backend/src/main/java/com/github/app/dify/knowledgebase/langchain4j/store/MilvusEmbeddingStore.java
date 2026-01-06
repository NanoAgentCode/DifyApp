package com.github.app.dify.knowledgebase.langchain4j.store;

import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;

/**
 * Milvus EmbeddingStore实现，适配Milvus存储
 * 支持按知识库ID隔离存储（每个知识库一个集合）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class MilvusEmbeddingStore extends BaseEmbeddingStore {

    public static MilvusEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, 
                                                        VectorStoreStrategy strategy) {
        MilvusEmbeddingStore store = new MilvusEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
}
