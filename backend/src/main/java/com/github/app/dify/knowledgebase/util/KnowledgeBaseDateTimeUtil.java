package com.github.app.dify.knowledgebase.util;

import com.github.app.dify.common.util.EntityLifecycleUtil;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;

/**
 * 知识库日期时间工具类
 * 提供知识库相关实体的日期时间处理方法
 */
public class KnowledgeBaseDateTimeUtil {
    
    /**
     * 设置知识库的创建时间和更新时间
     * 适用于新建知识库
     * 
     * @param knowledgeBase 知识库实体
     */
    public static void setCreateAndUpdateTime(KnowledgeBase knowledgeBase) {
        EntityLifecycleUtil.setCreateAndUpdateTime(knowledgeBase);
    }
    
    /**
     * 设置知识库的更新时间
     * 适用于更新知识库
     * 
     * @param knowledgeBase 知识库实体
     */
    public static void setUpdateTime(KnowledgeBase knowledgeBase) {
        EntityLifecycleUtil.setUpdateTime(knowledgeBase);
    }
    
    /**
     * 设置知识库文档的创建时间和更新时间
     * 适用于新建文档
     * 
     * @param document 知识库文档实体
     */
    public static void setCreateAndUpdateTime(KnowledgeBaseDocument document) {
        EntityLifecycleUtil.setCreateAndUpdateTime(document);
    }
    
    /**
     * 设置知识库文档的更新时间
     * 适用于更新文档
     * 
     * @param document 知识库文档实体
     */
    public static void setUpdateTime(KnowledgeBaseDocument document) {
        EntityLifecycleUtil.setUpdateTime(document);
    }
    
    /**
     * 设置向量数据库的创建时间和更新时间
     * 适用于新建向量数据库
     * 
     * @param vectorDatabase 向量数据库实体
     */
    public static void setCreateAndUpdateTime(VectorDatabase vectorDatabase) {
        EntityLifecycleUtil.setCreateAndUpdateTime(vectorDatabase);
    }
    
    /**
     * 设置向量数据库的更新时间
     * 适用于更新向量数据库
     * 
     * @param vectorDatabase 向量数据库实体
     */
    public static void setUpdateTime(VectorDatabase vectorDatabase) {
        EntityLifecycleUtil.setUpdateTime(vectorDatabase);
    }
}
