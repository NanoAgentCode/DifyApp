package com.github.app.dify.knowledgebase.service;

import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.req.TestVectorDatabaseConnectionRequest;
import com.github.app.dify.knowledgebase.req.VectorDatabaseRequest;
import com.github.app.dify.knowledgebase.resp.VectorDatabaseResp;
import java.util.List;
/**
 * 向量数据库配置服务接口
 */
public interface VectorDatabaseService {
    
    /**
     * 获取所有向量数据库配置
     */
    List<VectorDatabaseResp> getAllConfigs();
    
    /**
     * 根据类型获取配置列表
     */
    List<VectorDatabaseResp> getConfigsByType(String type);
    
    /**
     * 获取默认配置（按类型）
     */
    VectorDatabase getDefaultConfigByType(String type);
    
    /**
     * 更新配置
     */
    Object updateConfig(VectorDatabaseRequest request);
    
    /**
     * 测试连接
     */
    void testConnection(TestVectorDatabaseConnectionRequest request);
}