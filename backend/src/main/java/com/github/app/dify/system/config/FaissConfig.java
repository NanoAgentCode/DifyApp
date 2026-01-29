package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * FAISS 本地向量存储配置（可选，无配置时使用数据库中的向量库配置）
 */
@Component
public class FaissConfig {

    @Value("${faiss.base-path:./data/faiss}")
    private String basePath;

    public String getMetadataFilePath(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return basePath + File.separator + "default" + File.separator + "metadata.json";
        }
        return basePath + File.separator + knowledgeBaseId + File.separator + "metadata.json";
    }

    /** 重新加载配置（由 VectorDatabaseServiceImpl 在切换默认配置时调用） */
    public void reload() {
    }
}
