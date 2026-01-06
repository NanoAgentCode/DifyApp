package com.github.app.dify.chat.util;

import com.github.app.dify.chat.domain.AiApp;
import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.chat.domain.ChatMessage;
import com.github.app.dify.chat.resp.AiAppResp;
import com.github.app.dify.chat.resp.ChatConversationResponse;
import com.github.app.dify.chat.resp.ChatMessageResponse;
import org.springframework.beans.BeanUtils;

/**
 * 聊天实体转换工具类
 * 提供聊天相关实体的转换方法
 */
public class ChatConverterUtil {
    
    /**
     * 将 AiApp 转换为 AiAppResp
     * 
     * @param aiApp AI应用实体
     * @return AI应用响应对象
     */
    public static AiAppResp convertToResp(AiApp aiApp) {
        if (aiApp == null) {
            return null;
        }
        
        AiAppResp resp = new AiAppResp();
        BeanUtils.copyProperties(aiApp, resp);
        return resp;
    }
    
    /**
     * 将 ChatConversation 转换为 ChatConversationResponse
     * 
     * @param conversation 会话实体
     * @return 会话响应对象
     */
    public static ChatConversationResponse convertToResponse(ChatConversation conversation) {
        if (conversation == null) {
            return null;
        }
        
        ChatConversationResponse response = new ChatConversationResponse();
        response.setId(conversation.getId());
        response.setUserId(conversation.getUserId());
        response.setAppId(conversation.getAppId());
        response.setKnowledgeBaseId(conversation.getKnowledgeBaseId());
        response.setTitle(conversation.getTitle());
        response.setCreateTime(conversation.getCreateTime());
        response.setUpdateTime(conversation.getUpdateTime());
        return response;
    }
    
    /**
     * 将 ChatMessage 转换为 ChatMessageResponse
     * 
     * @param message 消息实体
     * @return 消息响应对象
     */
    public static ChatMessageResponse convertToMessageResponse(ChatMessage message) {
        if (message == null) {
            return null;
        }
        
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setConversationId(message.getConversationId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}

