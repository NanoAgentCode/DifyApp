package com.github.app.dify.knowledgebase.langchain4j;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * LangChain4j配置类
 */
@Configuration
public class LangChain4jConfig {
    
    @Autowired
    private ConfigurableDocumentSplitter configurableDocumentSplitter;
    
    @Autowired
    private VectorStoreFactory vectorStoreFactory;
    
    /**
     * DocumentSplitter Bean
     */
    @Bean
    public DocumentSplitter documentSplitter() {
        return configurableDocumentSplitter;
    }
    
    /**
     * 为指定知识库创建EmbeddingStore Bean（工厂方法）
     * 注意：由于每个知识库需要独立的EmbeddingStore，这里不创建全局Bean
     * 而是通过VectorStoreFactory根据知识库配置选择合适的策略创建
     */
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> createEmbeddingStore(Long knowledgeBaseId) {
        return vectorStoreFactory.createEmbeddingStore(knowledgeBaseId);
    }
}