package com.github.app.dify.model.util;

import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.knowledgebase.domain.EmbeddingModel;
import com.github.app.dify.knowledgebase.domain.QAModel;

import java.util.Date;

/**
 * 模型日期时间工具类
 * 提供模型相关实体的日期时间处理方法
 */
public class ModelDateTimeUtil {
    
    /**
     * 设置问答模型的创建时间和更新时间
     * 适用于新建问答模型
     * 
     * @param qaModel 问答模型实体
     */
    public static void setCreateAndUpdateTime(QAModel qaModel) {
        Date now = DateTimeUtil.now();
        qaModel.setCreateTime(now);
        qaModel.setUpdateTime(now);
    }
    
    /**
     * 设置问答模型的更新时间
     * 适用于更新问答模型
     * 
     * @param qaModel 问答模型实体
     */
    public static void setUpdateTime(QAModel qaModel) {
        qaModel.setUpdateTime(DateTimeUtil.now());
    }
    
    /**
     * 设置向量化模型的创建时间和更新时间
     * 适用于新建向量化模型
     * 
     * @param embeddingModel 向量化模型实体
     */
    public static void setCreateAndUpdateTime(EmbeddingModel embeddingModel) {
        Date now = DateTimeUtil.now();
        embeddingModel.setCreateTime(now);
        embeddingModel.setUpdateTime(now);
    }
    
    /**
     * 设置向量化模型的更新时间
     * 适用于更新向量化模型
     * 
     * @param embeddingModel 向量化模型实体
     */
    public static void setUpdateTime(EmbeddingModel embeddingModel) {
        embeddingModel.setUpdateTime(DateTimeUtil.now());
    }
}

