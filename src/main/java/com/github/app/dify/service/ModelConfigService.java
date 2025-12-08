package com.github.app.dify.service;

import com.github.app.dify.domain.EmbeddingModel;
import com.github.app.dify.domain.QAModel;
import com.github.app.dify.req.ModelConfigRequest;
import com.github.app.dify.req.TestModelConnectionRequest;
import com.github.app.dify.resp.ModelConfigResponse;
import com.github.app.dify.resp.QAModelResp;
import java.util.List;
/**
 * 模型配置服务接口
 */
public interface ModelConfigService {
    
    /**
     * 获取所有模型配置
     */
    ModelConfigResponse getModelConfig();
    
    /**
     * 获取可用的问答模型列表（根据使用场景）
     */
    List<QAModelResp> getAvailableQAModels(String useFor);
    
    /**
     * 获取向量化模型（根据ID，如果为null则返回默认模型）
     */
    EmbeddingModel getEmbeddingModelById(Long modelId);
    
    /**
     * 获取问答模型（根据ID）
     */
    QAModel getQAModelById(Long modelId);
    
    /**
     * 获取默认的RAG问答模型
     */
    QAModel getDefaultQAModelForRAG();
    
    /**
     * 更新模型配置
     */
    Object updateModelConfig(ModelConfigRequest request);
    
    /**
     * 测试模型连接
     */
    void testModelConnection(TestModelConnectionRequest request);
}