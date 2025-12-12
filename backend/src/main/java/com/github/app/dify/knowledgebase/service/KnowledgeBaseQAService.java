package com.github.app.dify.knowledgebase.service;

import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseQAResponse;
import reactor.core.publisher.Flux;
/**
 * 知识库问答服务接口（使用LangChain4j RAG）
 */
public interface KnowledgeBaseQAService {
    
    /**
     * 问答（非流式）
     */
    KnowledgeBaseQAResponse answer(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId, Integer userRole);
    
    /**
     * 问答（流式）
     */
    Flux<KnowledgeBaseQAResponse> answerStream(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId, Integer userRole);
}