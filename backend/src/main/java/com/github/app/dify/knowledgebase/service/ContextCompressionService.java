package com.github.app.dify.knowledgebase.service;

import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import com.github.app.dify.documentreader.req.DocumentQARequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
/**
 * 上下文压缩服务接口
 * 用于在连续对话时压缩历史上下文，避免超过token限制
 */
public interface ContextCompressionService {
    
    /**
     * 压缩历史对话消息（知识库问答）
     * 
     * @param messages 原始消息列表（包含系统消息、历史对话和当前问题）
     * @param request 当前请求
     * @return 压缩后的消息列表
     */
    List<ChatMessage> compressContext(List<ChatMessage> messages, KnowledgeBaseQARequest request);
    
    /**
     * 压缩历史对话消息（文档问答）
     * 
     * @param messages 原始消息列表（包含系统消息、历史对话和当前问题）
     * @param request 当前请求
     * @return 压缩后的消息列表
     */
    List<ChatMessage> compressContext(List<ChatMessage> messages, DocumentQARequest request);
    
    /**
     * 压缩系统消息中的文档内容（检索到的文档片段）
     * 当系统消息包含大量检索内容时，只保留最相关的部分
     * 
     * @param systemMessageText 系统消息文本
     * @param maxLength 最大长度（字符数）
     * @return 压缩后的系统消息文本
     */
    String compressDocumentContent(String systemMessageText, int maxLength);
    
    /**
     * 判断是否需要压缩
     */
    boolean needsCompression(List<ChatMessage> messages);
        }