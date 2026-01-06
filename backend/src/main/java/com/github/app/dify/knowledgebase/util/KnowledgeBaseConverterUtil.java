package com.github.app.dify.knowledgebase.util;

import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseDocumentResp;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseResp;
import com.github.app.dify.knowledgebase.resp.VectorDatabaseResp;
import org.springframework.beans.BeanUtils;

/**
 * 知识库实体转换工具类
 * 提供知识库相关实体的转换方法
 */
public class KnowledgeBaseConverterUtil {
    
    /**
     * 将 KnowledgeBase 转换为 KnowledgeBaseResp
     * 
     * @param knowledgeBase 知识库实体
     * @param documentRepository 文档仓库（用于查询文档数量）
     * @return 知识库响应对象
     */
    public static KnowledgeBaseResp convertToResp(
            KnowledgeBase knowledgeBase, 
            KnowledgeBaseDocumentRepository documentRepository) {
        if (knowledgeBase == null) {
            return null;
        }
        
        KnowledgeBaseResp resp = new KnowledgeBaseResp();
        BeanUtils.copyProperties(knowledgeBase, resp);
        
        if (documentRepository != null && knowledgeBase.getId() != null) {
            // 查询实际的文档数量
            Long documentCount = documentRepository.countByKnowledgeBaseId(knowledgeBase.getId());
            resp.setDocumentCount(documentCount != null ? documentCount.intValue() : 0);
            
            // 查询成功向量化的文档数量（向量化状态为2）
            Long successCount = documentRepository.countSuccessDocumentsByKnowledgeBaseId(knowledgeBase.getId());
            resp.setSuccessDocumentCount(successCount != null ? successCount.intValue() : 0);
            
            // 查询向量化失败的文档数量（向量化状态为3）
            Long failedCount = documentRepository.countFailedDocumentsByKnowledgeBaseId(knowledgeBase.getId());
            resp.setFailedDocumentCount(failedCount != null ? failedCount.intValue() : 0);
        }
        
        return resp;
    }
    
    /**
     * 将 KnowledgeBaseDocument 转换为 KnowledgeBaseDocumentResp
     * 
     * @param document 知识库文档实体
     * @return 知识库文档响应对象
     */
    public static KnowledgeBaseDocumentResp convertToResp(KnowledgeBaseDocument document) {
        if (document == null) {
            return null;
        }
        
        KnowledgeBaseDocumentResp resp = new KnowledgeBaseDocumentResp();
        BeanUtils.copyProperties(document, resp);
        return resp;
    }
    
    /**
     * 将 VectorDatabase 转换为 VectorDatabaseResp
     * 自动隐藏敏感信息（API Key）
     * 
     * @param config 向量数据库配置实体
     * @return 向量数据库响应对象
     */
    public static VectorDatabaseResp convertToResp(VectorDatabase config) {
        if (config == null) {
            return null;
        }
        
        VectorDatabaseResp resp = new VectorDatabaseResp();
        BeanUtils.copyProperties(config, resp);
        
        // 隐藏敏感信息（API Key只显示前4位和后4位）
        if (config.getApiKey() != null && config.getApiKey().length() > 8) {
            String apiKey = config.getApiKey();
            resp.setApiKey(apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
        } else if (config.getApiKey() != null) {
            resp.setApiKey("****");
        }
        
        return resp;
    }
}

