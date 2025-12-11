package com.github.app.dify.appknowledgebase.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.appsystemdata.config.ProviderType;
import com.github.app.dify.appknowledgebase.domain.QAModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * 模型语言模型工厂，根据模型配置动态创建模型实例
 */
@Component
public class ModelLanguageModelFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelLanguageModelFactory.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 创建非流式聊天模型
     */
    public ChatLanguageModel createChatLanguageModel(QAModel qaModel) {
        return new ChatLanguageModel() {
            private WebClient webClient;
            
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                try {
                    WebClient client = getWebClient(qaModel);
                    
                    // 构建请求体
                    Map<String, Object> requestBody = buildRequestBody(messages, qaModel);
                    
                    // 调用LLM API
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
            
            private WebClient getWebClient(QAModel model) {
                if (webClient == null) {
                    String baseUrl = buildApiUrl(model);
                    ProviderType providerType = ProviderType.fromValue(model.getProvider());
                    
                    WebClient.Builder builder = WebClient.builder()
                            .baseUrl(baseUrl)
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
                    
                    if (providerType.requiresApiKey() && model.getApiKey() != null && !model.getApiKey().trim().isEmpty()) {
                        builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + model.getApiKey());
                    }
                    
                    webClient = builder.build();
                    logger.info("ChatLanguageModel WebClient已创建，Provider: {}, URL: {}", providerType.getValue(), baseUrl);
                }
                return webClient;
            }
        };
    }
    
    /**
     * 创建流式聊天模型
     */
    public StreamingChatLanguageModel createStreamingChatLanguageModel(QAModel qaModel) {
        return new StreamingChatLanguageModel() {
            private WebClient webClient;
            
            @Override
            public Flux<String> generateStream(List<ChatMessage> messages) {
                try {
                    WebClient client = getWebClient(qaModel);
                    
                    // 构建请求体
                    Map<String, Object> requestBody = buildRequestBody(messages, qaModel);
                    requestBody.put("stream", true);
                    
                    // 调用流式LLM API
                    ProviderType providerType = ProviderType.fromValue(qaModel.getProvider());
                    logger.info("发送流式请求 - Provider: {}, Model: {}", providerType.getValue(), qaModel.getModel());
                    Flux<String> responseFlux = client.post()
                            .uri("")
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(status -> status.isError(), response -> {
                                logger.error("LLM API 返回错误状态: {}", response.statusCode());
                                return response.bodyToMono(String.class)
                                        .doOnNext(body -> logger.error("错误响应体: {}", body.length() > 500 ? body.substring(0, 500) + "..." : body))
                                        .then(Mono.error(new RuntimeException("LLM API 错误: " + response.statusCode())));
                            })
                            .bodyToFlux(DataBuffer.class)
                            .timeout(Duration.ofSeconds(300))
                            .doOnError(error -> {
                                logger.error("接收流式响应时发生错误", error);
                            })
                            .map(dataBuffer -> {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                DataBufferUtils.release(dataBuffer);
                                return new String(bytes, StandardCharsets.UTF_8);
                            })
                            .scan(Tuples.<String, List<String>>of("", new ArrayList<String>()), (state, chunk) -> {
                                String buffer = state.getT1() + chunk;
                                List<String> lines = new ArrayList<>();
                                String[] splitLines = buffer.split("\n", -1);
                                
                                String lastLine = splitLines[splitLines.length - 1];
                                for (int i = 0; i < splitLines.length - 1; i++) {
                                    String line = splitLines[i];
                                    if (i == 0 && !state.getT1().isEmpty()) {
                                        line = state.getT1() + line;
                                    }
                                    String trimmed = line.trim();
                                    if (!trimmed.isEmpty()) {
                                        lines.add(trimmed);
                                    }
                                }
                                
                                return Tuples.of(lastLine, lines);
                            })
                            .flatMap(state -> Flux.fromIterable(state.getT2()))
                            .filter(line -> line != null && !line.trim().isEmpty())
                            .<String>handle((line, sink) -> {
                                try {
                                    String chunk = parseStreamChunk(line);
                                    if (chunk != null && !chunk.isEmpty()) {
                                        sink.next(chunk);
                                    }
                                    // 静默跳过空chunk和null，减少日志噪音
                                } catch (Exception e) {
                                    logger.warn("解析流式响应块失败，跳过该行: {}", 
                                            line.length() > 100 ? line.substring(0, 100) + "..." : line, e);
                                }
                            });
                    
                    return responseFlux;
                    
                } catch (Exception e) {
                    logger.error("调用流式LLM API失败", e);
                    return Flux.error(new RuntimeException("调用流式LLM API失败: " + e.getMessage(), e));
                }
            }
            
            private WebClient getWebClient(QAModel model) {
                if (webClient == null) {
                    String baseUrl = buildApiUrl(model);
                    ProviderType providerType = ProviderType.fromValue(model.getProvider());
                    
                    WebClient.Builder builder = WebClient.builder()
                            .baseUrl(baseUrl)
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
                    
                    if (providerType.requiresApiKey() && model.getApiKey() != null && !model.getApiKey().trim().isEmpty()) {
                        builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + model.getApiKey());
                    }
                    
                    webClient = builder.build();
                    logger.info("StreamingChatLanguageModel WebClient已创建，Provider: {}, URL: {}", providerType.getValue(), baseUrl);
                }
                return webClient;
            }
        };
    }
    
    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, QAModel qaModel) {
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
        
        // 添加模型名称
        if (qaModel.getModel() != null && !qaModel.getModel().trim().isEmpty()) {
            requestBody.put("model", qaModel.getModel());
        }
        
        return requestBody;
    }
    
    /**
     * 解析非流式响应
     * 支持多种格式：
     * 1. OpenAI格式：{"choices":[{"message":{"content":"answer"}}]}
     * 2. Ollama格式：{"message":{"content":"answer"}}
     * 3. 通用格式：{"text":"answer"} 或 {"content":"answer"}
     */
    private String parseResponse(String responseJson) {
        if (responseJson == null || responseJson.trim().isEmpty()) {
            logger.warn("响应JSON为空");
            return "响应为空";
        }
        
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);
            
            // OpenAI格式：{"choices":[{"message":{"content":"answer"}}]}
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                com.fasterxml.jackson.databind.JsonNode choice = root.get("choices").get(0);
                if (choice.has("message")) {
                    com.fasterxml.jackson.databind.JsonNode message = choice.get("message");
                    if (message.has("content")) {
                        return message.get("content").asText();
                    }
                }
                if (choice.has("text")) {
                    return choice.get("text").asText();
                }
            }
            
            // Ollama格式：{"message":{"content":"answer"}}
            if (root.has("message")) {
                com.fasterxml.jackson.databind.JsonNode message = root.get("message");
                if (message.has("content")) {
                    return message.get("content").asText();
                }
            }
            
            // 通用格式
            if (root.has("text")) {
                return root.get("text").asText();
            }
            
            if (root.has("content")) {
                return root.get("content").asText();
            }
            
            logger.warn("无法解析LLM响应格式，返回原始JSON（前500字符）: {}", 
                    responseJson.length() > 500 ? responseJson.substring(0, 500) + "..." : responseJson);
            return responseJson;
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("解析LLM响应JSON失败: {}", e.getMessage());
            return "解析响应失败: " + e.getMessage();
        } catch (Exception e) {
            logger.error("解析LLM响应时发生异常", e);
            return "解析响应失败: " + e.getMessage();
        }
    }
    
    /**
     * 解析流式响应块
     * 支持多种格式：
     * 1. SSE格式（OpenAI兼容）：data: {...}
     * 2. 纯JSON行格式（Ollama）：{"message":{"content":"token"},"done":false}
     * 3. Ollama旧格式：{"response":"token","done":false}
     * 4. OpenAI格式：{"choices":[{"delta":{"content":"token"}}]}
     */
    private String parseStreamChunk(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        
        try {
            String jsonStr = extractJsonString(line);
            if (jsonStr == null || jsonStr.isEmpty()) {
                return null;
            }
            
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonStr);
            
            // 优先处理Ollama新格式：{"message":{"role":"assistant","content":"token"},"done":false}
            String content = extractFromOllamaMessage(root);
            if (content != null) {
                return content;
            }
            
            // 处理Ollama旧格式：{"response":"token","done":false}
            content = extractFromOllamaResponse(root);
            if (content != null) {
                return content;
            }
            
            // 处理OpenAI兼容格式：{"choices":[{"delta":{"content":"token"}}]}
            content = extractFromOpenAIChoices(root);
            if (content != null) {
                return content;
            }
            
            // 处理其他可能的格式
            content = extractFromGenericFields(root);
            return content;
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // JSON解析失败，可能是非JSON行（如空行、注释等），静默跳过
            return null;
        } catch (Exception e) {
            logger.debug("解析流式响应块时发生异常: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从行中提取JSON字符串
     */
    private String extractJsonString(String line) {
        String trimmed = line.trim();
        
        // SSE格式：data: {...}
        if (trimmed.startsWith("data: ")) {
            String jsonStr = trimmed.substring(6).trim();
            if (jsonStr.equals("[DONE]")) {
                return null;
            }
            return jsonStr;
        }
        
        // 纯JSON行格式：直接是JSON对象
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        
        return null;
    }
    
    /**
     * 从Ollama message格式中提取content
     * 格式：{"message":{"role":"assistant","content":"token"},"done":false}
     */
    private String extractFromOllamaMessage(com.fasterxml.jackson.databind.JsonNode root) {
        if (!root.has("message")) {
            return null;
        }
        
        com.fasterxml.jackson.databind.JsonNode message = root.get("message");
        if (!message.has("content")) {
            return null;
        }
        
        String content = message.get("content").asText();
        boolean done = root.has("done") && root.get("done").asBoolean();
        
        // 如果done为true且content为空，表示流结束
        if (done && content.isEmpty()) {
            return null;
        }
        
        // 返回content（即使done为true，如果content不为空，也应该返回最后一个token）
        return content.isEmpty() ? null : content;
    }
    
    /**
     * 从Ollama旧格式中提取response
     * 格式：{"response":"token","done":false}
     */
    private String extractFromOllamaResponse(com.fasterxml.jackson.databind.JsonNode root) {
        if (!root.has("response")) {
            return null;
        }
        
        String response = root.get("response").asText();
        boolean done = root.has("done") && root.get("done").asBoolean();
        
        if (done && response.isEmpty()) {
            return null;
        }
        
        return response.isEmpty() ? null : response;
    }
    
    /**
     * 从OpenAI兼容格式中提取content
     * 格式：{"choices":[{"delta":{"content":"token"}}]}
     */
    private String extractFromOpenAIChoices(com.fasterxml.jackson.databind.JsonNode root) {
        if (!root.has("choices") || !root.get("choices").isArray() || root.get("choices").size() == 0) {
            return null;
        }
        
        com.fasterxml.jackson.databind.JsonNode choice = root.get("choices").get(0);
        
        // 优先从delta中获取content
        if (choice.has("delta")) {
            com.fasterxml.jackson.databind.JsonNode delta = choice.get("delta");
            if (delta.has("content")) {
                return delta.get("content").asText();
            }
        }
        
        // 如果没有delta，尝试从text字段获取
        if (choice.has("text")) {
            return choice.get("text").asText();
        }
        
        return null;
    }
    
    /**
     * 从通用字段中提取内容
     * 支持：text、content等字段
     */
    private String extractFromGenericFields(com.fasterxml.jackson.databind.JsonNode root) {
        if (root.has("text")) {
            String text = root.get("text").asText();
            return text.isEmpty() ? null : text;
        }
        
        if (root.has("content")) {
            String content = root.get("content").asText();
            return content.isEmpty() ? null : content;
        }
        
        return null;
    }
    
    /**
     * 根据 provider 类型构建完整的 API URL
     */
    private String buildApiUrl(QAModel qaModel) {
        String apiUrl = qaModel.getApiUrl();
        ProviderType providerType = ProviderType.fromValue(qaModel.getProvider());
        
        // 如果 URL 已经包含路径（包含 /api/ 或 /v1/），则直接使用
        if (apiUrl.contains("/api/") || apiUrl.contains("/v1/")) {
            return apiUrl;
        }
        
        // 根据 provider 类型添加相应的路径
        String path = providerType.getChatPath();
        return apiUrl.endsWith("/") ? apiUrl + path.substring(1) : apiUrl + path;
    }
    
    /**
     * 聊天语言模型接口
     */
    public interface ChatLanguageModel {
        Response<AiMessage> generate(List<ChatMessage> messages);
    }
    
    /**
     * 流式聊天语言模型接口
     */
    public interface StreamingChatLanguageModel {
        Flux<String> generateStream(List<ChatMessage> messages);
    }
}