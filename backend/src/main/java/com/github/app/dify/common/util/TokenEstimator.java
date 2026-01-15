package com.github.app.dify.common.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token估算工具类
 * 提供统一的Token使用量估算方法
 */
public class TokenEstimator {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenEstimator.class);
    
    /**
     * 估算Token使用量（基于内容长度）
     * 这是一个简单的估算方法，实际token数可能因模型而异
     * 一般规则：中文约1.5字符=1token，英文约4字符=1token
     * 
     * @param messages 所有输入消息
     * @param answer 回答内容
     * @return [promptTokens, completionTokens, totalTokens]
     */
    public static Long[] estimateTokenUsage(ChatMessage[] messages, String answer) {
        long promptTokens = 0;
        long completionTokens = 0;
        
        // 估算prompt tokens（所有输入消息）
        for (ChatMessage msg : messages) {
            String content = "";
            if (msg instanceof UserMessage) {
                content = ((UserMessage) msg).singleText();
            } else if (msg instanceof SystemMessage) {
                content = ((SystemMessage) msg).text();
            } else if (msg instanceof AiMessage) {
                content = ((AiMessage) msg).text();
            }
            if (content != null && !content.isEmpty()) {
                promptTokens += estimateTextTokens(content);
            }
        }
        
        // 估算completion tokens（回答内容）
        if (answer != null && !answer.isEmpty()) {
            completionTokens = estimateTextTokens(answer);
        }
        
        long totalTokens = promptTokens + completionTokens;
        
        logger.debug("估算Token使用量 - Prompt: {}, Completion: {}, Total: {}", 
                promptTokens, completionTokens, totalTokens);
        
        return new Long[]{promptTokens, completionTokens, totalTokens};
    }
    
    /**
     * 估算文本的Token数量
     * 
     * @param text 文本内容
     * @return Token数量
     */
    public static long estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 简单估算：中文字符数/1.5 + 英文字符数/4
        long chineseChars = text.chars().filter(ch -> ch >= 0x4E00 && ch <= 0x9FFF).count();
        long otherChars = text.length() - chineseChars;
        return (long)(chineseChars / 1.5 + otherChars / 4);
    }
    
    /**
     * 估算单条消息的Token数量
     * 
     * @param message ChatMessage
     * @return Token数量
     */
    public static long estimateMessageTokens(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        
        String content = "";
        if (message instanceof UserMessage) {
            content = ((UserMessage) message).singleText();
        } else if (message instanceof SystemMessage) {
            content = ((SystemMessage) message).text();
        } else if (message instanceof AiMessage) {
            content = ((AiMessage) message).text();
        }
        
        return estimateTextTokens(content);
    }
}
