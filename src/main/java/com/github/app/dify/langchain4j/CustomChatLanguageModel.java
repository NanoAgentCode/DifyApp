package com.github.app.dify.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义ChatLanguageModel，适配现有的LLM API
 * 注意：此类已废弃，请使用ModelLanguageModelFactory创建的模型实例
 */
@Component
public class CustomChatLanguageModel implements ChatLanguageModel {
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        throw new IllegalStateException("CustomChatLanguageModel已废弃，请使用ModelLanguageModelFactory创建的模型实例。请在管理端大模型管理页面配置问答模型。");
    }
}

