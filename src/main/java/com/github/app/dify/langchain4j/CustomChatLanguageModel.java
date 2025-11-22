package com.github.app.dify.langchain4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.config.RagConfig;
import com.github.app.dify.config.ProviderType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义ChatLanguageModel，适配现有的LLM API
 */
@Component
public class CustomChatLanguageModel implements ChatLanguageModel {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomChatLanguageModel.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private WebClient llmWebClient;
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        try {
            if (ragConfig.getLlmApiUrl() == null || ragConfig.getLlmApiUrl().trim().isEmpty()) {
                throw new IllegalStateException("LLM API URL未配置，请在application.yml中配置rag.llm-api-url");
            }
            
            WebClient client = getLlmWebClient();
            
            // 构建请求体
            Map<String, Object> requestBody = buildRequestBody(messages);
            
            // 调用LLM API（URL 已经在 baseUrl 中设置，这里不需要再添加路径）
            String responseJson = client.post()
                    .uri("")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();
            
            // 解析响应
            String answer = parseResponse(responseJson);
            
            return Response.from(AiMessage.from(answer));
            
        } catch (Exception e) {
            logger.error("调用LLM API失败", e);
            throw new RuntimeException("调用LLM API失败: " + e.getMessage(), e);
        }
    }
    
    // 注意：这个方法签名可能与接口不匹配，如果编译错误可以删除
    // @Override
    // public Response<AiMessage> generate(List<ChatMessage> messages, List<dev.langchain4j.model.input.Prompt> prompts) {
    //     // 合并prompts到messages中
    //     List<ChatMessage> allMessages = new ArrayList<>(messages);
    //     if (prompts != null) {
    //         for (dev.langchain4j.model.input.Prompt prompt : prompts) {
    //             allMessages.add(UserMessage.from(prompt.text()));
    //         }
    //     }
    //     return generate(allMessages);
    // }
    
    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(List<ChatMessage> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        
        // 转换消息格式
        List<Map<String, String>> apiMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, String> apiMessage = new HashMap<>();
            
            if (message instanceof SystemMessage) {
                apiMessage.put("role", "system");
                apiMessage.put("content", ((SystemMessage) message).text());
            } else if (message instanceof UserMessage) {
                apiMessage.put("role", "user");
                apiMessage.put("content", ((UserMessage) message).singleText());
            } else if (message instanceof AiMessage) {
                apiMessage.put("role", "assistant");
                apiMessage.put("content", ((AiMessage) message).text());
            }
            
            apiMessages.add(apiMessage);
        }
        
        requestBody.put("messages", apiMessages);
        requestBody.put("stream", false);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);
        
        // 添加模型名称（如果配置了）
        if (ragConfig.getLlmModel() != null && !ragConfig.getLlmModel().trim().isEmpty()) {
            requestBody.put("model", ragConfig.getLlmModel());
        }
        
        return requestBody;
    }
    
    /**
     * 解析响应
     */
    private String parseResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            
            // 尝试多种响应格式
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode choice = root.get("choices").get(0);
                if (choice.has("message")) {
                    JsonNode message = choice.get("message");
                    if (message.has("content")) {
                        return message.get("content").asText();
                    }
                }
                if (choice.has("text")) {
                    return choice.get("text").asText();
                }
            }
            
            if (root.has("text")) {
                return root.get("text").asText();
            }
            
            if (root.has("content")) {
                return root.get("content").asText();
            }
            
            logger.warn("无法解析LLM响应，返回原始JSON: {}", responseJson);
            return responseJson;
            
        } catch (Exception e) {
            logger.error("解析LLM响应失败", e);
            return "解析LLM响应失败: " + e.getMessage();
        }
    }
    
    /**
     * 根据 provider 类型构建完整的 API URL
     */
    private String buildApiUrl() {
        String apiUrl = ragConfig.getLlmApiUrl();
        ProviderType providerType = ProviderType.fromValue(ragConfig.getProvider());
        
        // 如果 URL 已经包含路径（包含 /api/ 或 /v1/），则直接使用
        if (apiUrl.contains("/api/") || apiUrl.contains("/v1/")) {
            return apiUrl;
        }
        
        // 根据 provider 类型添加相应的路径
        String path = providerType.getChatPath();
        return apiUrl.endsWith("/") ? apiUrl + path.substring(1) : apiUrl + path;
    }
    
    /**
     * 获取LLM WebClient
     */
    private WebClient getLlmWebClient() {
        if (llmWebClient == null && ragConfig.getLlmApiUrl() != null) {
            String baseUrl = buildApiUrl();
            String provider = ragConfig.getProvider() != null ? ragConfig.getProvider().toLowerCase() : "openai";
            
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    // 增加缓冲区大小以支持大响应（默认256KB，增加到10MB）
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
            
            // 根据 provider 类型决定是否需要 API Key
            ProviderType providerType = ProviderType.fromValue(ragConfig.getProvider());
            if (providerType.requiresApiKey() && ragConfig.getLlmApiKey() != null && !ragConfig.getLlmApiKey().trim().isEmpty()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ragConfig.getLlmApiKey());
            }
            
            llmWebClient = builder.build();
            logger.info("CustomChatLanguageModel WebClient已创建，Provider: {}, URL: {}", providerType.getValue(), baseUrl);
        }
        return llmWebClient;
    }
}

