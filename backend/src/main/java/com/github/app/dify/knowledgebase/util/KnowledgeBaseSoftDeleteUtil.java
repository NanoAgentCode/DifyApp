package com.github.app.dify.knowledgebase.util;

import com.github.app.dify.common.util.EntityLifecycleUtil;
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
        EntityLifecycleUtil.softDelete(knowledgeBase, repository);
    }
    
    /**
     * 软删除知识库文档（设置 deleted = 1 和 updateTime）
     * 
     * @param document 知识库文档实体
     * @param repository 知识库文档仓库
     */
    public static void softDelete(KnowledgeBaseDocument document, CrudRepository<KnowledgeBaseDocument, Long> repository) {
        EntityLifecycleUtil.softDelete(document, repository);
    }
    
    /**
     * 软删除向量数据库（设置 deleted = 1 和 updateTime）
     * 
     * @param vectorDatabase 向量数据库实体
     * @param repository 向量数据库仓库
     */
    public static void softDelete(VectorDatabase vectorDatabase, CrudRepository<VectorDatabase, Long> repository) {
        EntityLifecycleUtil.softDelete(vectorDatabase, repository);
    }
    
    /**
     * 恢复软删除的知识库（设置 deleted = 0 和 updateTime）
     * 
     * @param knowledgeBase 知识库实体
     * @param repository 知识库仓库
     */
    public static void restore(KnowledgeBase knowledgeBase, CrudRepository<KnowledgeBase, Long> repository) {
        EntityLifecycleUtil.restore(knowledgeBase, repository);
    }
    
    /**
     * 检查知识库是否已删除
     * 
     * @param knowledgeBase 知识库实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(KnowledgeBase knowledgeBase) {
        return EntityLifecycleUtil.isDeleted(knowledgeBase);
    }
    
    /**
     * 检查知识库文档是否已删除
     * 
     * @param document 知识库文档实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(KnowledgeBaseDocument document) {
        return EntityLifecycleUtil.isDeleted(document);
    }
    
    /**
     * 检查向量数据库是否已删除
     * 
     * @param vectorDatabase 向量数据库实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(VectorDatabase vectorDatabase) {
        return EntityLifecycleUtil.isDeleted(vectorDatabase);
    }
}
