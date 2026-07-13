package com.github.app.dify.knowledgebase.service.strategy;

import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import com.github.app.dify.knowledgebase.util.VectorDatabaseConfigHelper;
import com.github.app.dify.system.config.DocumentReaderConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective vector database configuration for a knowledge base.
 * A document-reader request uses the configured document-reader instance; all
 * other requests prefer the instance bound to the knowledge base, then fall
 * back to the default instance for the requested storage type.
 */
@Service
@RequiredArgsConstructor
class VectorDatabaseConfigResolver {

    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseConfigResolver.class);

    private final VectorDatabaseConfigHelper configHelper;
    private final DocumentReaderConfig documentReaderConfig;
    private final VectorDatabaseRepository vectorDatabaseRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    VectorDatabase resolve(Long knowledgeBaseId, String vectorStoreType) {
        try {
            VectorDatabase configuredInstance = resolveBoundInstance(knowledgeBaseId);
            if (configuredInstance != null) {
                return configuredInstance;
            }

            VectorDatabase defaultConfig = configHelper.getConfigByType(vectorStoreType);
            if (defaultConfig != null) {
                logger.debug("使用默认{}配置 - 知识库ID: {}", vectorStoreType, knowledgeBaseId);
            }
            return defaultConfig;
        } catch (Exception exception) {
            logger.warn("获取向量数据库配置失败 - 知识库ID: {}, 类型: {}", knowledgeBaseId, vectorStoreType, exception);
            return null;
        }
    }

    private VectorDatabase resolveBoundInstance(Long knowledgeBaseId) {
        Long vectorDatabaseId = null;
        if (Long.valueOf(0L).equals(knowledgeBaseId)) {
            vectorDatabaseId = documentReaderConfig.getVectorDatabaseId();
        } else if (knowledgeBaseId != null) {
            vectorDatabaseId = knowledgeBaseRepository.findById(knowledgeBaseId)
                    .map(KnowledgeBase::getVectorDatabaseId)
                    .orElse(null);
        }

        if (vectorDatabaseId == null) {
            return null;
        }

        Long configuredId = vectorDatabaseId;
        return vectorDatabaseRepository.findById(configuredId)
                .orElseGet(() -> {
                    logger.warn("绑定的向量数据库不存在 - 知识库ID: {}, 配置ID: {}", knowledgeBaseId, configuredId);
                    return null;
                });
    }
}
