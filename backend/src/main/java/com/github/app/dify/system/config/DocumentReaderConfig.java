package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文档解读模块配置（默认 QA 模型、向量库、思维导图服务等）
 */
@Component
public class DocumentReaderConfig {

    @Value("${document-reader.default-qa-model-id:#{null}}")
    private Long defaultQAModelId;

    @Value("${document-reader.default-embedding-model-id:#{null}}")
    private Long defaultEmbeddingModelId;

    @Value("${document-reader.vector-database-id:#{null}}")
    private Long vectorDatabaseId;

    @Value("${document-reader.vector-store-type:pgvector}")
    private String vectorStoreType;

    @Value("${document-reader.mind-map-service-url:}")
    private String mindMapServiceUrl;

    @Value("${document-reader.top-k:10}")
    private Integer topK;

    public Long getDefaultQAModelId() {
        return defaultQAModelId;
    }

    public Long getDefaultEmbeddingModelId() {
        return defaultEmbeddingModelId;
    }

    public Long getVectorDatabaseId() {
        return vectorDatabaseId;
    }

    public String getVectorStoreType() {
        return vectorStoreType;
    }

    public String getMindMapServiceUrl() {
        return mindMapServiceUrl;
    }

    public Integer getTopK() {
        return topK;
    }
}
