package com.github.app.dify.chat.util;

import com.github.app.dify.chat.domain.AiApp;
import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.chat.domain.ChatMessage;

import java.util.Date;

/**
 * 聊天日期时间工具类
 * 提供统一的日期时间处理方法
 */
public class ChatDateTimeUtil {
    
    /**
     * 获取当前时间
     * 
     * @return 当前时间
     */
    public static Date now() {
        return new Date();
    }
    
    /**
     * 设置AI应用的创建时间和更新时间
     * 适用于新建AI应用
     * 
     * @param aiApp AI应用实体
     */
    public static void setCreateAndUpdateTime(AiApp aiApp) {
        Date now = now();
        aiApp.setCreateTime(now);
        aiApp.setUpdateTime(now);
    }
    
    /**
     * 设置AI应用的更新时间
     * 适用于更新AI应用
     * 
     * @param aiApp AI应用实体
     */
    public static void setUpdateTime(AiApp aiApp) {
        aiApp.setUpdateTime(now());
    }
    
    /**
     * 设置会话的创建时间和更新时间
     * 适用于新建会话
     * 
     * @param conversation 会话实体
     */
    public static void setCreateAndUpdateTime(ChatConversation conversation) {
        Date now = now();
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
    }
    
    /**
     * 设置会话的更新时间
     * 适用于更新会话
     * 
     * @param conversation 会话实体
     */
    public static void setUpdateTime(ChatConversation conversation) {
        conversation.setUpdateTime(now());
    }
    
    /**
     * 设置消息的创建时间
     * 适用于新建消息
     * 
     * @param message 消息实体
     */
    public static void setCreateTime(ChatMessage message) {
        message.setCreateTime(now());
    }
}

