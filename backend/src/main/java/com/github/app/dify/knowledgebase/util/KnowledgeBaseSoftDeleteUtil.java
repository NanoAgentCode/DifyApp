package com.github.app.dify.knowledgebase.util;

import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import org.springframework.data.repository.CrudRepository;

/**
 * 知识库软删除工具类
 * 提供统一的软删除操作方法
 */
public class KnowledgeBaseSoftDeleteUtil {
    
    /**
     * 软删除知识库（设置 deleted = 1 和 updateTime）
     * 
     * @param knowledgeBase 知识库实体
     * @param repository 知识库仓库
     */
    public static void softDelete(KnowledgeBase knowledgeBase, CrudRepository<KnowledgeBase, Long> repository) {
        knowledgeBase.setDeleted(1);
        KnowledgeBaseDateTimeUtil.setUpdateTime(knowledgeBase);
        repository.save(knowledgeBase);
    }
    
    /**
     * 软删除知识库文档（设置 deleted = 1 和 updateTime）
     * 
     * @param document 知识库文档实体
     * @param repository 知识库文档仓库
     */
    public static void softDelete(KnowledgeBaseDocument document, CrudRepository<KnowledgeBaseDocument, Long> repository) {
        document.setDeleted(1);
        KnowledgeBaseDateTimeUtil.setUpdateTime(document);
        repository.save(document);
    }
    
    /**
     * 软删除向量数据库（设置 deleted = 1 和 updateTime）
     * 
     * @param vectorDatabase 向量数据库实体
     * @param repository 向量数据库仓库
     */
    public static void softDelete(VectorDatabase vectorDatabase, CrudRepository<VectorDatabase, Long> repository) {
        vectorDatabase.setDeleted(1);
        KnowledgeBaseDateTimeUtil.setUpdateTime(vectorDatabase);
        repository.save(vectorDatabase);
    }
    
    /**
     * 恢复软删除的知识库（设置 deleted = 0 和 updateTime）
     * 
     * @param knowledgeBase 知识库实体
     * @param repository 知识库仓库
     */
    public static void restore(KnowledgeBase knowledgeBase, CrudRepository<KnowledgeBase, Long> repository) {
        knowledgeBase.setDeleted(0);
        KnowledgeBaseDateTimeUtil.setUpdateTime(knowledgeBase);
        repository.save(knowledgeBase);
    }
    
    /**
     * 检查知识库是否已删除
     * 
     * @param knowledgeBase 知识库实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(KnowledgeBase knowledgeBase) {
        return knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1;
    }
    
    /**
     * 检查知识库文档是否已删除
     * 
     * @param document 知识库文档实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(KnowledgeBaseDocument document) {
        return document.getDeleted() != null && document.getDeleted() == 1;
    }
    
    /**
     * 检查向量数据库是否已删除
     * 
     * @param vectorDatabase 向量数据库实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(VectorDatabase vectorDatabase) {
        return vectorDatabase.getDeleted() != null && vectorDatabase.getDeleted() == 1;
    }
}

