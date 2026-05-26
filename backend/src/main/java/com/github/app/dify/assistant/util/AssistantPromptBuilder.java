package com.github.app.dify.assistant.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.assistant.req.AssistantChatReq;
import com.github.app.dify.system.util.SkillLoader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AssistantPromptBuilder {

    private final ObjectMapper objectMapper;

    public AssistantPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(String message, AssistantChatReq.AssistantPageContext pageContext) {
        String contextJson = "{}";
        try {
            if (pageContext != null) {
                contextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pageContext);
            }
        } catch (Exception ignored) {
            contextJson = "{}";
        }

        return SkillLoader.loadSkillWithTemplate("assistant/page_chat_prompt", Map.of(
                "contextJson", contextJson,
                "message", String.valueOf(message)));
    }
}
