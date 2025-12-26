package com.github.app.dify.documentreader.service;

import com.github.app.dify.documentreader.req.DocumentQARequest;
import com.github.app.dify.documentreader.resp.DocumentQAResponse;
import reactor.core.publisher.Flux;

/**
 * 文档解读问答服务接口
 */
public interface DocumentReaderQAService {
    
    /**
     * 文档问答（非流式）
     * @param documentId 文档ID
     * @param request 问答请求
     * @param userId 用户ID
     * @return 问答响应
     */
    DocumentQAResponse answer(Long documentId, DocumentQARequest request, Long userId);
    
    /**
     * 文档问答（流式）
     * @param documentId 文档ID
     * @param request 问答请求
     * @param userId 用户ID
     * @return 流式问答响应
     */
    Flux<DocumentQAResponse> answerStream(Long documentId, DocumentQARequest request, Long userId);
}

