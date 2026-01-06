package com.github.app.dify.chat.util;

import com.github.app.dify.chat.domain.AiApp;
import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.chat.domain.ChatMessage;
import com.github.app.dify.common.util.DateTimeUtil;

import java.util.Date;

/**
 * 聊天日期时间工具类
 * 提供聊天相关实体的日期时间处理方法
 */
public class ChatDateTimeUtil {
    
    /**
     * 设置AI应用的创建时间和更新时间
     * 适用于新建AI应用
     * 
     * @param aiApp AI应用实体
     */
    public static void setCreateAndUpdateTime(AiApp aiApp) {
        Date now = DateTimeUtil.now();
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
        aiApp.setUpdateTime(DateTimeUtil.now());
    }
    
    /**
     * 设置会话的创建时间和更新时间
     * 适用于新建会话
     * 
     * @param conversation 会话实体
     */
    public static void setCreateAndUpdateTime(ChatConversation conversation) {
        Date now = DateTimeUtil.now();
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
        conversation.setUpdateTime(DateTimeUtil.now());
    }
    
    /**
     * 设置消息的创建时间
     * 适用于新建消息
     * 
     * @param message 消息实体
     */
    public static void setCreateTime(ChatMessage message) {
        message.setCreateTime(DateTimeUtil.now());
    }
}

