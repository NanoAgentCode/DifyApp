package com.github.app.dify.knowledgebase.langchain4j;

import dev.langchain4j.data.message.ChatMessage;
import reactor.core.publisher.Flux;
import java.util.List;

/**
 * 流式聊天语言模型接口
 */
public interface StreamingChatLanguageModel {
    /**
     * 流式生成聊天回复
     * @param messages 聊天消息列表
     * @return 流式返回的文本块
     */
    Flux<String> generateStream(List<ChatMessage> messages);
}

