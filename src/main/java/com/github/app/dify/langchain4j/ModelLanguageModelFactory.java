package com.github.app.dify.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.config.ProviderType;
import com.github.app.dify.domain.QAModel;
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
                    logger.info("发送流式请求 - Provider: {}, Model: {}, RequestBody: {}", 
                            ProviderType.fromValue(qaModel.getProvider()).getValue(), 
                            qaModel.getModel(), 
                            requestBody);
                    Flux<String> responseFlux = client.post()
                            .uri("")
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(status -> status.isError(), response -> {
                                logger.error("Ollama API 返回错误状态: {}", response.statusCode());
                                return response.bodyToMono(String.class)
                                        .doOnNext(body -> logger.error("错误响应体: {}", body))
                                        .then(Mono.error(new RuntimeException("Ollama API 错误: " + response.statusCode())));
                            })
                            .bodyToFlux(DataBuffer.class)
                            .timeout(Duration.ofSeconds(300))
                            .doOnSubscribe(subscription -> {
                                logger.info("开始订阅Ollama流式响应");
                            })
                            .doOnNext(dataBuffer -> {
                                logger.info("收到数据块，大小: {} bytes", dataBuffer.readableByteCount());
                            })
                            .doOnError(error -> {
                                logger.error("接收Ollama流式响应时发生错误", error);
                            })
                            .map(dataBuffer -> {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                DataBufferUtils.release(dataBuffer);
                                String text = new String(bytes, StandardCharsets.UTF_8);
                                logger.info("数据块内容 (前200字符): {}", text.length() > 200 ? text.substring(0, 200) + "..." : text);
                                return text;
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
                            .doOnNext(line -> {
                                logger.info("处理行 (前200字符): {}", line.length() > 200 ? line.substring(0, 200) + "..." : line);
                            })
                            .doOnComplete(() -> {
                                logger.info("所有数据块处理完成");
                            })
                            .<String>handle((line, sink) -> {
                                try {
                                    String chunk = parseStreamChunk(line);
                                    if (chunk != null) {
                                        // chunk可能为空字符串，这也是有效的（表示没有新token）
                                        // 但空字符串不应该发送，因为会导致无意义的响应
                                        if (!chunk.isEmpty()) {
                                            logger.info("解析出token (前50字符): {}", chunk.length() > 50 ? chunk.substring(0, 50) + "..." : chunk);
                                            sink.next(chunk);
                                        } else {
                                            logger.debug("解析出空token，跳过");
                                        }
                                    } else {
                                        logger.debug("解析结果为null，跳过该行: {}", line.substring(0, Math.min(100, line.length())));
                                    }
                                } catch (Exception e) {
                                    logger.error("解析流式响应块时发生异常，行内容: {}", line.substring(0, Math.min(200, line.length())), e);
                                }
                            })
                            .doOnComplete(() -> {
                                logger.info("Token流处理完成");
                            })
                            .doOnError(error -> {
                                logger.error("Token流处理发生错误", error);
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
     * 解析响应
     */
    private String parseResponse(String responseJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);
            
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
     * 解析流式响应块
     * 支持两种格式：
     * 1. SSE格式（OpenAI兼容）：data: {...}
     * 2. 纯JSON行格式（Ollama）：直接是JSON对象
     */
    private String parseStreamChunk(String line) {
        try {
            String jsonStr = null;
            
            // SSE格式：data: {...}
            if (line.startsWith("data: ")) {
                jsonStr = line.substring(6).trim();
                if (jsonStr.equals("[DONE]")) {
                    return null;
                }
            } else if (line.trim().startsWith("{")) {
                // 纯JSON行格式（Ollama）：直接是JSON对象
                jsonStr = line.trim();
            } else {
                // 其他格式，跳过
                return null;
            }
            
            if (jsonStr == null || jsonStr.isEmpty()) {
                return null;
            }
            
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonStr);
            
            // Ollama格式：{"message":{"role":"assistant","content":"token"},"done":false}
            if (root.has("message")) {
                com.fasterxml.jackson.databind.JsonNode message = root.get("message");
                if (message.has("content")) {
                    String content = message.get("content").asText();
                    boolean done = root.has("done") && root.get("done").asBoolean();
                    logger.info("Ollama解析 - content: '{}' (长度: {}), done: {}", content, content.length(), done);
                    // 如果 done 为 true，表示流结束，但最后一个content可能还有内容，应该返回
                    if (done) {
                        // 即使done为true，如果content不为空，也应该返回（这是最后一个token）
                        if (content.isEmpty()) {
                            logger.debug("Ollama流结束，content为空，返回null");
                            return null;
                        } else {
                            logger.info("Ollama流结束，但还有content: '{}'", content);
                            return content;
                        }
                    }
                    // content可能为空字符串（某些情况下），空字符串也应该返回，让上层处理
                    logger.info("Ollama返回content: '{}'", content);
                    return content;
                } else {
                    logger.warn("Ollama message存在但没有content字段: {}", message.toString());
                }
            }
            
            // Ollama旧格式：{"response":"token","done":false}
            if (root.has("response")) {
                String response = root.get("response").asText();
                // 如果 done 为 true，表示流结束，返回 null
                if (root.has("done") && root.get("done").asBoolean()) {
                    return response.isEmpty() ? null : response;
                }
                return response.isEmpty() ? null : response;
            }
            
            // OpenAI兼容格式：{"choices":[{"delta":{"content":"token"}}]}
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                com.fasterxml.jackson.databind.JsonNode choice = root.get("choices").get(0);
                if (choice.has("delta")) {
                    com.fasterxml.jackson.databind.JsonNode delta = choice.get("delta");
                    if (delta.has("content")) {
                        return delta.get("content").asText();
                    }
                }
                if (choice.has("text")) {
                    return choice.get("text").asText();
                }
            }
            
            // 其他可能的格式
            if (root.has("text")) {
                return root.get("text").asText();
            }
            
            if (root.has("content")) {
                return root.get("content").asText();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.debug("解析流式响应块失败（可能是非JSON行）: {}", line, e);
            return null;
        }
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

