package com.github.app.dify.knowledgebase.langchain4j.store;

import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;

/**
 * FAISS EmbeddingStore实现，适配FAISS存储
 * 支持按知识库ID隔离存储（每个知识库一个索引文件）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class FaissEmbeddingStore extends BaseEmbeddingStore {

    public static FaissEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, 
                                                        VectorStoreStrategy strategy) {
        FaissEmbeddingStore store = new FaissEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
}
