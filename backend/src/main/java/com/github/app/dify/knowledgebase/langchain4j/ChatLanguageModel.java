package com.github.app.dify.knowledgebase.langchain4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * 聊天语言模型接口
 */
public interface ChatLanguageModel {
    /**
     * 生成聊天回复
     * @param messages 聊天消息列表
     * @return AI回复消息
     */
    Response<AiMessage> generate(List<ChatMessage> messages);
}

