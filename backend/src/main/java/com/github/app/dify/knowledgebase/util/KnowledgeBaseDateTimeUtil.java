package com.github.app.dify.knowledgebase.util;

import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;

import java.util.Date;

/**
 * 知识库日期时间工具类
 * 提供统一的日期时间处理方法
 */
public class KnowledgeBaseDateTimeUtil {
    
    /**
     * 获取当前时间
     * 
     * @return 当前时间
     */
    public static Date now() {
        return new Date();
    }
    
    /**
     * 设置知识库的创建时间和更新时间
     * 适用于新建知识库
     * 
     * @param knowledgeBase 知识库实体
     */
    public static void setCreateAndUpdateTime(KnowledgeBase knowledgeBase) {
        Date now = now();
        knowledgeBase.setCreateTime(now);
        knowledgeBase.setUpdateTime(now);
    }
    
    /**
     * 设置知识库的更新时间
     * 适用于更新知识库
     * 
     * @param knowledgeBase 知识库实体
     */
    public static void setUpdateTime(KnowledgeBase knowledgeBase) {
        knowledgeBase.setUpdateTime(now());
    }
    
    /**
     * 设置知识库文档的创建时间和更新时间
     * 适用于新建文档
     * 
     * @param document 知识库文档实体
     */
    public static void setCreateAndUpdateTime(KnowledgeBaseDocument document) {
        Date now = now();
        document.setCreateTime(now);
        document.setUpdateTime(now);
    }
    
    /**
     * 设置知识库文档的更新时间
     * 适用于更新文档
     * 
     * @param document 知识库文档实体
     */
    public static void setUpdateTime(KnowledgeBaseDocument document) {
        document.setUpdateTime(now());
    }
    
    /**
     * 设置向量数据库的创建时间和更新时间
     * 适用于新建向量数据库
     * 
     * @param vectorDatabase 向量数据库实体
     */
    public static void setCreateAndUpdateTime(VectorDatabase vectorDatabase) {
        Date now = now();
        vectorDatabase.setCreateTime(now);
        vectorDatabase.setUpdateTime(now);
    }
    
    /**
     * 设置向量数据库的更新时间
     * 适用于更新向量数据库
     * 
     * @param vectorDatabase 向量数据库实体
     */
    public static void setUpdateTime(VectorDatabase vectorDatabase) {
        vectorDatabase.setUpdateTime(now());
    }
}

