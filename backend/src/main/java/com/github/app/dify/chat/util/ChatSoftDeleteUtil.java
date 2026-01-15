package com.github.app.dify.chat.util;

import com.github.app.dify.chat.domain.AiApp;
import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.common.util.EntityLifecycleUtil;
import org.springframework.data.repository.CrudRepository;

/**
 * 聊天软删除工具类
 * 提供统一的软删除操作方法
 */
public class ChatSoftDeleteUtil {
    
    /**
     * 软删除AI应用（设置 deleted = 1 和 updateTime）
     * 
     * @param aiApp AI应用实体
     * @param repository AI应用仓库
     */
    public static void softDelete(AiApp aiApp, CrudRepository<AiApp, Long> repository) {
        EntityLifecycleUtil.softDelete(aiApp, repository);
    }
    
    /**
     * 软删除会话（设置 deleted = 1 和 updateTime）
     * 
     * @param conversation 会话实体
     * @param repository 会话仓库
     */
    public static void softDelete(ChatConversation conversation, CrudRepository<ChatConversation, Long> repository) {
        EntityLifecycleUtil.softDelete(conversation, repository);
    }
    
    /**
     * 恢复软删除的AI应用（设置 deleted = 0 和 updateTime）
     * 
     * @param aiApp AI应用实体
     * @param repository AI应用仓库
     */
    public static void restore(AiApp aiApp, CrudRepository<AiApp, Long> repository) {
        EntityLifecycleUtil.restore(aiApp, repository);
    }
    
    /**
     * 检查AI应用是否已删除
     * 
     * @param aiApp AI应用实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(AiApp aiApp) {
        return EntityLifecycleUtil.isDeleted(aiApp);
    }
    
    /**
     * 检查会话是否已删除
     * 
     * @param conversation 会话实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(ChatConversation conversation) {
        return EntityLifecycleUtil.isDeleted(conversation);
    }
}
