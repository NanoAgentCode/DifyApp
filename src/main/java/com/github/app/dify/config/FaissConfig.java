package com.github.app.dify.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Paths;

/**
 * FAISS配置类
 * 用于配置FAISS向量存储的基础路径
 */
@Configuration
@ConfigurationProperties(prefix = "faiss")
public class FaissConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(FaissConfig.class);
    
    /**
     * FAISS索引文件的基础存储路径
     * 每个知识库的索引文件将存储在：{basePath}/kb_{knowledgeBaseId}/
     */
    private String basePath = "./data/faiss";
    
    public String getBasePath() {
        return basePath;
    }
    
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
    
    /**
     * 获取知识库的FAISS索引目录路径
     * @param knowledgeBaseId 知识库ID
     * @return 索引目录路径
     */
    public String getKnowledgeBasePath(Long knowledgeBaseId) {
        String path = Paths.get(basePath, "kb_" + knowledgeBaseId).toString();
        // 确保目录存在
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.debug("创建FAISS索引目录: {}", path);
            } else {
                logger.warn("创建FAISS索引目录失败: {}", path);
            }
        }
        return path;
    }
    
    /**
     * 获取知识库的FAISS索引文件路径
     * @param knowledgeBaseId 知识库ID
     * @return 索引文件路径
     */
    public String getIndexFilePath(Long knowledgeBaseId) {
        return Paths.get(getKnowledgeBasePath(knowledgeBaseId), "index.faiss").toString();
    }
    
    /**
     * 获取知识库的元数据文件路径
     * @param knowledgeBaseId 知识库ID
     * @return 元数据文件路径
     */
    public String getMetadataFilePath(Long knowledgeBaseId) {
        return Paths.get(getKnowledgeBasePath(knowledgeBaseId), "metadata.json").toString();
    }
}

