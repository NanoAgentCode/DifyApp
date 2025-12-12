package com.github.app.dify.knowledgebase.service;

import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
/**
 * 上下文压缩服务接口
 * 用于在连续对话时压缩历史上下文，避免超过token限制
 */
public interface ContextCompressionService {
    
    /**
     * 压缩历史对话消息
     * 
     * @param messages 原始消息列表（包含系统消息、历史对话和当前问题）
     * @param request 当前请求
     * @return 压缩后的消息列表
     */
    List<ChatMessage> compressContext(List<ChatMessage> messages, KnowledgeBaseQARequest request);
    
    /**
     * 判断是否需要压缩
     */
    boolean needsCompression(List<ChatMessage> messages);
        }