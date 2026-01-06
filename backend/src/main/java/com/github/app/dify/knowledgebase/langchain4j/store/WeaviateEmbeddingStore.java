package com.github.app.dify.knowledgebase.langchain4j.store;

import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;

/**
 * Weaviate EmbeddingStore实现，适配Weaviate存储
 * 支持按知识库ID隔离存储（每个知识库一个类）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class WeaviateEmbeddingStore extends BaseEmbeddingStore {

    public static WeaviateEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, 
                                                        VectorStoreStrategy strategy) {
        WeaviateEmbeddingStore store = new WeaviateEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
}
