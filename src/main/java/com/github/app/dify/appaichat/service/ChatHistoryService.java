package com.github.app.dify.appaichat.service;

import com.github.app.dify.appaichat.req.ChatHistoryRequest;
import com.github.app.dify.appaichat.req.CreateConversationRequest;
import com.github.app.dify.appaichat.resp.ChatConversationResponse;
import com.github.app.dify.appaichat.resp.ChatHistoryStatisticsResponse;
import com.github.app.dify.appaichat.resp.ChatMessageResponse;
import com.github.app.dify.appcommon.resp.PageResponse;
import java.util.List;
import java.util.Map;
/**
 * 会话历史管理服务接口
 * 会话（Conversation）：一个完整的对话会话，包含多轮问答
 * 消息（Message）：会话中的单条消息，一问一答为一轮对话
 */
public interface ChatHistoryService {
    
    /**
     * 创建新会话
     */
    ChatConversationResponse createConversation(Long userId, CreateConversationRequest request);
    
    /**
     * 获取或创建会话（如果conversationId为空或不存在，则创建新会话）
     */
    Long getOrCreateConversation(Long userId, Long conversationId, Integer type, Long appId, Long knowledgeBaseId, String firstQuestion);
    
    /**
     * 保存消息
     */
    void saveMessage(Long conversationId, String role, String content);
    
    /**
     * 获取我的会话列表（分页）
     */
    PageResponse<ChatConversationResponse> getMyConversations(Long userId, ChatHistoryRequest request);
    
    /**
     * 获取所有会话列表（分页，管理员）
     */
    PageResponse<ChatConversationResponse> getAllConversations(ChatHistoryRequest request);
    
    /**
     * 获取会话详情
     */
    ChatConversationResponse getConversation(Long conversationId, Long userId, boolean isAdmin);
    
    /**
     * 获取会话消息列表
     */
    List<ChatMessageResponse> getMessages(Long conversationId, Long userId, boolean isAdmin);
    
    /**
     * 更新会话标题
     */
    void updateConversationTitle(Long conversationId, Long userId, String title, boolean isAdmin);
    
    /**
     * 删除会话
     */
    void deleteConversation(Long conversationId, Long userId, boolean isAdmin);
    
    /**
     * 批量删除会话
     */
    void batchDeleteConversations(List<Long> conversationIds);
    
    /**
     * 导出会话
     */
    Map<String, Object> exportConversation(Long conversationId, Long userId, boolean isAdmin);
    
    /**
     * 获取统计信息
     */
    ChatHistoryStatisticsResponse getStatistics();
}