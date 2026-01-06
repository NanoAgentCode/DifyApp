package com.github.app.dify.model.util;

import com.github.app.dify.knowledgebase.domain.EmbeddingModel;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.resp.EmbeddingModelResp;
import com.github.app.dify.knowledgebase.resp.QAModelResp;
import org.springframework.beans.BeanUtils;

/**
 * 模型实体转换工具类
 * 提供模型相关实体的转换方法
 */
public class ModelConverterUtil {
    
    /**
     * 将 QAModel 转换为 QAModelResp
     * 
     * @param qaModel 问答模型实体
     * @return 问答模型响应对象
     */
    public static QAModelResp convertToQAModelResp(QAModel qaModel) {
        if (qaModel == null) {
            return null;
        }
        
        QAModelResp resp = new QAModelResp();
        BeanUtils.copyProperties(qaModel, resp);
        return resp;
    }
    
    /**
     * 将 EmbeddingModel 转换为 EmbeddingModelResp
     * 
     * @param embeddingModel 向量化模型实体
     * @return 向量化模型响应对象
     */
    public static EmbeddingModelResp convertToEmbeddingModelResp(EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            return null;
        }
        
        EmbeddingModelResp resp = new EmbeddingModelResp();
        BeanUtils.copyProperties(embeddingModel, resp);
        return resp;
    }
}

