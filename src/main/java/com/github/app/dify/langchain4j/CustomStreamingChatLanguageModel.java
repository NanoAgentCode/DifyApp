package com.github.app.dify.langchain4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.config.RagConfig;
import com.github.app.dify.config.ProviderType;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
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
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义流式ChatLanguageModel工具类，支持流式响应
 * 注意：由于langchain4j版本兼容性问题，这里不实现StreamingChatLanguageModel接口
 * 而是提供独立的流式生成方法
 */
@Component
public class CustomStreamingChatLanguageModel {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomStreamingChatLanguageModel.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private WebClient llmWebClient;
    
    /**
     * 生成流式响应，返回Flux<String> tokens
     */
    public Flux<String> generateStream(List<ChatMessage> messages) {
        try {
            if (ragConfig.getLlmApiUrl() == null || ragConfig.getLlmApiUrl().trim().isEmpty()) {
                throw new IllegalStateException("LLM API URL未配置，请在application.yml中配置rag.llm-api-url");
            }
            
            WebClient client = getLlmWebClient();
            
            // 构建请求体
            Map<String, Object> requestBody = buildRequestBody(messages);
            
            // 调用流式LLM API（URL 已经在 baseUrl 中设置，这里不需要再添加路径）
            // 使用DataBuffer处理流式响应，然后按行分割
            Flux<String> responseFlux = client.post()
                    .uri("")
                    .accept(MediaType.TEXT_EVENT_STREAM)  // 明确指定接受SSE格式
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .timeout(Duration.ofSeconds(300))
                    .doOnSubscribe(subscription -> {
                        logger.info("开始订阅流式LLM API响应");
                    })
                    .doOnNext(dataBuffer -> {
                        logger.info("收到数据块，大小: {} 字节", dataBuffer.readableByteCount());
                    })
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return new String(bytes, StandardCharsets.UTF_8);
                    })
                    .scan(Tuples.<String, List<String>>of("", new ArrayList<String>()), (state, chunk) -> {
                        // state: Tuple2<buffer, lines>
                        // 累积数据块，处理跨块的行
                        String buffer = state.getT1() + chunk;
                        List<String> lines = new ArrayList<>();
                        String[] splitLines = buffer.split("\n", -1); // -1保留末尾空字符串
                        
                        // 最后一行可能不完整，保留在buffer中
                        String lastLine = splitLines[splitLines.length - 1];
                        for (int i = 0; i < splitLines.length - 1; i++) {
                            String line = splitLines[i];
                            if (i == 0 && !state.getT1().isEmpty()) {
                                // 第一行需要与前一个buffer的末尾合并
                                line = state.getT1() + line;
                            }
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                lines.add(trimmed);
                            }
                        }
                        
                        return Tuples.of(lastLine, lines);
                    })
                    .flatMap(state -> {
                        // 发出所有完整的行
                        return Flux.fromIterable(state.getT2());
                    })
                    .doOnNext(line -> {
                        logger.info("收到SSE数据行: {}", line.length() > 200 ? line.substring(0, 200) + "..." : line);
                    })
                    .doOnError(error -> {
                        logger.error("流式LLM API响应错误", error);
                    })
                    .doOnComplete(() -> {
                        logger.info("流式LLM API响应完成");
                    });
            
            // 返回tokens的Flux
            // 每一行是一个SSE事件，需要解析
            // 使用handle来处理null值，因为map不允许返回null
            return responseFlux
                    .filter(line -> {
                        boolean shouldKeep = line != null && !line.trim().isEmpty();
                        if (!shouldKeep) {
                            logger.info("过滤空行");
                        }
                        return shouldKeep;
                    })
                    .<String>handle((line, sink) -> {
                        try {
                            logger.info("开始解析SSE行: {}", line.length() > 200 ? line.substring(0, 200) + "..." : line);
                            String chunk = parseStreamChunk(line);
                            if (chunk != null && !chunk.isEmpty()) {
                                logger.info("解析到token，长度: {}, 内容: {}", chunk.length(), chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk);
                                sink.next(chunk);
                            } else {
                                logger.info("解析结果为空，跳过该行: {}", line.length() > 200 ? line.substring(0, 200) + "..." : line);
                            }
                        } catch (Exception e) {
                            logger.error("解析流式响应块时发生异常: {}", e.getMessage(), e);
                        }
                    })
                    .doOnNext(token -> {
                        logger.info("发出token，长度: {}, 内容: {}", token.length(), token.length() > 50 ? token.substring(0, 50) + "..." : token);
                    })
                    .doOnComplete(() -> {
                        logger.info("流式token处理完成");
                    });
            
        } catch (Exception e) {
            logger.error("调用流式LLM API失败", e);
            throw new RuntimeException("调用流式LLM API失败: " + e.getMessage(), e);
        }
    }
    
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
        requestBody.put("stream", true);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);
        
        // 添加模型名称（如果配置了）
        if (ragConfig.getLlmModel() != null && !ragConfig.getLlmModel().trim().isEmpty()) {
            requestBody.put("model", ragConfig.getLlmModel());
        }
        
        return requestBody;
    }
    
    /**
     * 解析流式响应块（SSE格式）
     */
    private String parseStreamChunk(String line) {
        try {
            String trimmedLine = line.trim();
            
            // 跳过空行
            if (trimmedLine.isEmpty()) {
                return null;
            }
            
            // SSE格式: data: {...} 或 data:{...}
            String jsonStr = trimmedLine;
            if (trimmedLine.startsWith("data: ")) {
                jsonStr = trimmedLine.substring(6).trim();
            } else if (trimmedLine.startsWith("data:")) {
                jsonStr = trimmedLine.substring(5).trim();
            }
            
            // 检查结束标记
            if ("[DONE]".equals(jsonStr)) {
                logger.debug("收到结束标记 [DONE]");
                return null;
            }
            
            // 跳过非data行（如event:、id:等），但记录日志以便调试
            if (!trimmedLine.startsWith("data")) {
                logger.info("跳过非data行: {}", trimmedLine.length() > 100 ? trimmedLine.substring(0, 100) : trimmedLine);
                return null;
            }
            
            // 如果jsonStr为空，说明可能是空data行
            if (jsonStr.isEmpty()) {
                logger.info("跳过空data行");
                return null;
            }
            
            // 解析JSON
            logger.info("准备解析JSON: {}", jsonStr.length() > 500 ? jsonStr.substring(0, 500) + "..." : jsonStr);
            JsonNode root = objectMapper.readTree(jsonStr);
            
            // 记录解析到的JSON结构（用于调试）
            java.util.Iterator<String> fieldNames = root.fieldNames();
            java.util.List<String> fieldList = new java.util.ArrayList<>();
            fieldNames.forEachRemaining(fieldList::add);
            logger.info("解析JSON成功，包含字段: {}", fieldList.isEmpty() ? "无字段" : String.join(", ", fieldList));
            
            // 尝试多种响应格式
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode choice = root.get("choices").get(0);
                java.util.Iterator<String> choiceFields = choice.fieldNames();
                java.util.List<String> choiceFieldList = new java.util.ArrayList<>();
                choiceFields.forEachRemaining(choiceFieldList::add);
                logger.info("找到choice对象，包含字段: {}", choiceFieldList.isEmpty() ? "无字段" : String.join(", ", choiceFieldList));
                
                if (choice.has("delta")) {
                    JsonNode delta = choice.get("delta");
                    java.util.Iterator<String> deltaFields = delta.fieldNames();
                    java.util.List<String> deltaFieldList = new java.util.ArrayList<>();
                    deltaFields.forEachRemaining(deltaFieldList::add);
                    logger.info("找到delta对象，包含字段: {}", deltaFieldList.isEmpty() ? "无字段" : String.join(", ", deltaFieldList));
                    
                    if (delta.has("content")) {
                        String content = delta.get("content").asText();
                        if (content != null && !content.isEmpty()) {
                            logger.info("从delta.content提取到内容，长度: {}", content.length());
                            return content;
                        } else {
                            logger.info("delta.content存在但为空或null");
                        }
                    } else {
                        logger.info("delta中没有content字段");
                    }
                } else {
                    logger.info("choice中没有delta字段");
                }
                
                if (choice.has("text")) {
                    String text = choice.get("text").asText();
                    if (text != null && !text.isEmpty()) {
                        logger.info("从choice.text提取到内容，长度: {}", text.length());
                        return text;
                    }
                }
                logger.warn("choice中没有找到delta.content或text字段");
            } else {
                logger.warn("JSON中没有choices字段或choices为空");
            }
            
            if (root.has("content")) {
                String content = root.get("content").asText();
                if (content != null && !content.isEmpty()) {
                    logger.info("从root.content提取到内容，长度: {}", content.length());
                    return content;
                }
            }
            
            // 如果都没有找到，记录完整的JSON以便调试
            logger.warn("无法从JSON中提取内容，完整JSON: {}", root.toString().length() > 1000 ? 
                    root.toString().substring(0, 1000) + "..." : root.toString());
            return null;
            
        } catch (Exception e) {
            logger.warn("解析流式响应块失败: {}, 错误: {}", 
                    line.length() > 100 ? line.substring(0, 100) + "..." : line, e.getMessage());
            logger.debug("解析错误详情", e);
            return null;
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
            logger.info("CustomStreamingChatLanguageModel WebClient已创建，Provider: {}, URL: {}", providerType.getValue(), baseUrl);
        }
        return llmWebClient;
    }
}

