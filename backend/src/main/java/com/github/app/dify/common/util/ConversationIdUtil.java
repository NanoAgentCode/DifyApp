package com.github.app.dify.common.util;

import org.slf4j.Logger;

/**
 * conversationId 解析工具。
 */
public final class ConversationIdUtil {

    private ConversationIdUtil() {
    }

    /**
     * 将 conversationId 字符串解析为 Long。
     * 无效或空值返回 null，并按需打印告警日志。
     */
    public static Long parseConversationId(String conversationId, Logger logger) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            if (logger != null) {
                logger.warn("无效的conversationId: {}", conversationId);
            }
            return null;
        }
    }

    /**
     * 透传 Long 类型 conversationId，便于与字符串解析入口统一调用。
     */
    public static Long parseConversationId(Long conversationId, Logger logger) {
        return conversationId;
    }
}
