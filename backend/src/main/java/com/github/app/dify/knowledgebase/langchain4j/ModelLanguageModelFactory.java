package com.github.app.dify.knowledgebase.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.system.config.ProviderType;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;

/**
 * 模型语言模型工厂，根据模型配置动态创建模型实例
 */
@Component
public class ModelLanguageModelFactory {

    private static final Logger logger = LoggerFactory.getLogger(ModelLanguageModelFactory.class);
    private static final String TRACE_SOURCE_USER_MEMORY_EXTRACTION = "User Memory Extraction";

    // 使用ThreadLocal存储当前请求的图片数据（用于多模态支持）
    private static final ThreadLocal<List<ChatRequest.ImageData>> imageDataContext = new ThreadLocal<>();

    // 使用ThreadLocal存储当前请求的Trace Source
    private static final ThreadLocal<String> traceSourceContext = new ThreadLocal<>();

    // 使用ThreadLocal存储当前请求的会话ID
    private static final ThreadLocal<String> conversationIdContext = new ThreadLocal<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TraceFacade traceFacade;

    /**
     * 设置当前请求的图片数据
     */
    public void setImageData(List<ChatRequest.ImageData> imageData) {
        imageDataContext.set(imageData);
    }

    /**
     * 设置当前请求的Trace Source
     */
    public void setTraceSource(String traceSource) {
        traceSourceContext.set(traceSource);
    }

    /**
     * 清除当前请求的Trace Source
     */
    public void clearTraceSource() {
        traceSourceContext.remove();
    }

    /**
     * 获取当前请求的Trace Source
     */
    public static String getTraceSource() {
        return traceSourceContext.get();
    }

    /**
     * 设置当前请求的会话ID
     */
    public void setConversationId(String conversationId) {
        conversationIdContext.set(conversationId);
    }

    /**
     * 清除当前请求的会话ID
     */
    public void clearConversationId() {
        conversationIdContext.remove();
    }

    /**
     * 获取当前请求的会话ID
     */
    private static String getConversationId() {
        return conversationIdContext.get();
    }

    /**
     * 清除当前请求的图片数据
     */
    public void clearImageData() {
        imageDataContext.remove();
    }

    /**
     * 获取当前请求的图片数据
     */
    private List<com.github.app.dify.chat.req.ChatRequest.ImageData> getImageData() {
        return imageDataContext.get();
    }

    /**
     * 创建非流式聊天模型
     */
    public ChatLanguageModel createChatLanguageModel(QAModel qaModel) {
        String traceSource = getTraceSource();
        boolean skipTrace = shouldSkipTrace(traceSource);
        return new ChatLanguageModel() {
            private WebClient webClient;

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                TraceHandle traceHandle = null;
                try {
                    WebClient client = getWebClient(qaModel);

                    // 构建请求体
                    Map<String, Object> requestBody = buildRequestBody(messages, qaModel);

                    // 记录开始
                    String conversationId = getConversationId(); // 从 ThreadLocal 获取
                    if (!skipTrace) {
                        try {
                            String requestJson = objectMapper.writeValueAsString(requestBody);
                            TraceStartRequest startRequest = new TraceStartRequest();
                            startRequest.setTraceSource(traceSource != null ? traceSource : "LLM");
                            startRequest.setConversationId(conversationId);
                            startRequest.setRequestType("llm_call");
                            startRequest.setRequestSummary(requestJson);
                            startRequest.setModel(qaModel.getModel());
                            startRequest.setProvider(qaModel.getProvider());
                            traceHandle = traceFacade.start(startRequest);
                        } catch (Exception e) {
                            logger.warn("记录LLM Trace Start失败", e);
                        }
                    }

                    // 调用LLM API，添加重试机制（增加重试次数和间隔）
                    String responseJson = client.post()
                            .uri("")
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(120))
                            .retryWhen(Retry.backoff(4, Duration.ofSeconds(2))
                                    .maxBackoff(Duration.ofSeconds(10))
                                    .filter(throwable -> {
                                        // 检查异常及其原因链，查找网络错误和连接重置
                                        Throwable current = throwable;
                                        while (current != null) {
                                            // 检查 SocketException
                                            if (current instanceof java.net.SocketException) {
                                                logger.warn("检测到网络连接错误，将重试: {}", current.getMessage());
                                                return true;
                                            }
                                            // 检查超时异常
                                            if (current instanceof java.util.concurrent.TimeoutException) {
                                                logger.warn("检测到超时错误，将重试: {}", current.getMessage());
                                                return true;
                                            }
                                            // 检查 WebClientRequestException（可能包装了 Connection reset）
                                            if (current instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
                                                String message = current.getMessage();
                                                if (message != null &&
                                                        (message.contains("Connection reset") ||
                                                                message.contains("connection reset") ||
                                                                message.contains("Connection refused") ||
                                                                message.contains("connection refused"))) {
                                                    logger.warn("检测到连接错误（WebClientRequestException），将重试: {}", message);
                                                    return true;
                                                }
                                            }
                                            // 检查异常消息中是否包含连接重置相关关键词
                                            String message = current.getMessage();
                                            if (message != null &&
                                                    (message.contains("Connection reset") ||
                                                            message.contains("connection reset") ||
                                                            message.contains("Connection refused") ||
                                                            message.contains("connection refused") ||
                                                            message.contains("Broken pipe") ||
                                                            message.contains("broken pipe"))) {
                                                logger.warn("检测到连接错误，将重试: {}", message);
                                                return true;
                                            }
                                            // 继续检查异常链
                                            current = current.getCause();
                                        }
                                        return false;
                                    })
                                    .doBeforeRetry(retrySignal -> logger.info("重试LLM API调用，第{}次重试（总重试次数: {}）",
                                            retrySignal.totalRetries() + 1, retrySignal.totalRetriesInARow() + 1)))
                            .block();

                    // 解析响应
                    String answer = parseResponse(responseJson);

                    // 记录结束
                    if (!skipTrace) {
                        try {
                            // 解析 token 使用信息
                            int[] usage = parseUsage(responseJson);
                            traceFacade.success(traceHandle, responseJson, usage[0], usage[1], usage[2]);
                        } catch (Exception e) {
                            logger.warn("记录LLM Trace End失败", e);
                        }
                    }

                    return Response.from(AiMessage.from(answer));

                } catch (Exception e) {
                    logger.error("调用LLM API失败", e);
                    if (!skipTrace) {
                        try {
                            traceFacade.error(traceHandle, e);
                        } catch (Exception ex) {
                            logger.warn("记录LLM Trace Error失败", ex);
                        }
                    }
                    throw new BusinessException("调用LLM API失败", ErrorCode.API_CALL_FAILED, e);
                }
            }

            private WebClient getWebClient(QAModel model) {
                if (webClient == null) {
                    String baseUrl = buildApiUrl(model);
                    ProviderType providerType = ProviderType.fromValue(model.getProvider());

                    // 配置 HttpClient 连接和读取超时
                    HttpClient httpClient = HttpClient.create()
                            .responseTimeout(Duration.ofSeconds(120))
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000); // 30秒连接超时

                    WebClient.Builder builder = WebClient.builder()
                            .baseUrl(baseUrl)
                            .clientConnector(
                                    new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));

                    if (providerType.requiresApiKey() && model.getApiKey() != null
                            && !model.getApiKey().trim().isEmpty()) {
                        builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + model.getApiKey());
                    }

                    webClient = builder.build();
                    logger.info("ChatLanguageModel WebClient已创建，Provider: {}, URL: {}", providerType.getValue(),
                            baseUrl);
                }
                return webClient;
            }
        };
    }

    /**
     * 创建流式聊天模型
     */
    public StreamingChatLanguageModel createStreamingChatLanguageModel(QAModel qaModel) {
        String traceSource = getTraceSource();
        boolean skipTrace = shouldSkipTrace(traceSource);
        return new StreamingChatLanguageModel() {
            private WebClient webClient;

            @Override
            public Flux<String> generateStream(List<ChatMessage> messages) {
                TraceHandle traceHandle = null;
                try {
                    WebClient client = getWebClient(qaModel);

                    // 构建请求体（支持多模态）
                    Map<String, Object> requestBody = buildRequestBody(messages, qaModel);
                    requestBody.put("stream", true);

                    // 调用流式LLM API
                    ProviderType providerType = ProviderType.fromValue(qaModel.getProvider());
                    logger.info("发送流式请求 - Provider: {}, Model: {}", providerType.getValue(), qaModel.getModel());

                    // 记录开始
                    String conversationId = getConversationId(); // 从 ThreadLocal 获取
                    if (!skipTrace) {
                        try {
                            String requestJson = objectMapper.writeValueAsString(requestBody);
                            TraceStartRequest startRequest = new TraceStartRequest();
                            startRequest.setTraceSource(traceSource != null ? traceSource : "LLM");
                            startRequest.setConversationId(conversationId);
                            startRequest.setRequestType("llm_stream_call");
                            startRequest.setRequestSummary(requestJson);
                            startRequest.setModel(qaModel.getModel());
                            startRequest.setProvider(qaModel.getProvider());
                            traceHandle = traceFacade.start(startRequest);
                        } catch (Exception e) {
                            logger.warn("记录Stream LLM Trace Start失败", e);
                        }
                    }

                    StringBuffer fullResponse = new StringBuffer();
                    final TraceHandle finalTraceHandle = traceHandle;

                    return client.post()
                            .uri("")
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_NDJSON,
                                    MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, response -> {
                                logger.error("LLM API 返回错误状态: {}", response.statusCode());
                                return response.bodyToMono(String.class)
                                        .doOnNext(body -> logger.error("错误响应体: {}",
                                                body.length() > 500 ? body.substring(0, 500) + "..." : body))
                                        .then(Mono
                                                .error(new BusinessException("LLM API错误", ErrorCode.API_CALL_FAILED)));
                            })
                            .bodyToFlux(DataBuffer.class)
                            .timeout(Duration.ofSeconds(300))
                            .retryWhen(Retry.backoff(2, Duration.ofSeconds(3))
                                    .maxBackoff(Duration.ofSeconds(10))
                                    .filter(throwable -> {
                                        // 流式请求重试2次，检查异常链
                                        Throwable current = throwable;
                                        while (current != null) {
                                            if (current instanceof java.net.SocketException) {
                                                logger.warn("流式响应检测到网络连接错误，将重试: {}", current.getMessage());
                                                return true;
                                            }
                                            if (current instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
                                                String message = current.getMessage();
                                                if (message != null &&
                                                        (message.contains("Connection reset") ||
                                                                message.contains("connection reset") ||
                                                                message.contains("Connection refused") ||
                                                                message.contains("connection refused"))) {
                                                    logger.warn("流式响应检测到连接错误（WebClientRequestException），将重试: {}",
                                                            message);
                                                    return true;
                                                }
                                            }
                                            String message = current.getMessage();
                                            if (message != null &&
                                                    (message.contains("Connection reset") ||
                                                            message.contains("connection reset") ||
                                                            message.contains("Connection refused") ||
                                                            message.contains("connection refused"))) {
                                                logger.warn("流式响应检测到连接错误，将重试: {}", message);
                                                return true;
                                            }
                                            current = current.getCause();
                                        }
                                        return false;
                                    })
                                    .doBeforeRetry(retrySignal -> logger.info("重试流式LLM API调用，第{}次重试（总重试次数: {}）",
                                            retrySignal.totalRetries() + 1, retrySignal.totalRetriesInARow() + 1)))
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
                            })
                            .doOnNext(chunk -> {
                                if (chunk != null) {
                                    fullResponse.append(chunk);
                                }
                            })
                            .doOnComplete(() -> {
                                try {
                                    // 流式响应估算 token（约 4 字符 = 1 token）
                                    int outputTokens = Math.max(1, fullResponse.length() / 4);
                                    traceFacade.success(finalTraceHandle, fullResponse.toString(), 0, outputTokens,
                                            outputTokens);
                                } catch (Exception e) {
                                    logger.warn("记录Stream LLM Trace End失败", e);
                                }
                            })
                            .doOnError(e -> {
                                try {
                                    traceFacade.error(finalTraceHandle, e);
                                } catch (Exception ex) {
                                    logger.warn("记录Stream LLM Trace Error失败", ex);
                                }
                            })
                            .doFinally(signalType -> {
                                if (signalType == SignalType.CANCEL) {
                                    logger.info("Stream cancelled by client, spanId: {}",
                                            finalTraceHandle != null ? finalTraceHandle.getSpanId() : "null");
                                    // Optionally record as error or just specialized log
                                }
                            });

                } catch (Exception e) {
                    logger.error("调用流式LLM API失败", e);
                    return Flux.error(new BusinessException("调用流式LLM API失败", ErrorCode.API_CALL_FAILED, e));
                }
            }

            private WebClient getWebClient(QAModel model) {
                if (webClient == null) {
                    String baseUrl = buildApiUrl(model);
                    ProviderType providerType = ProviderType.fromValue(model.getProvider());

                    // 配置 HttpClient 连接和读取超时（流式请求需要更长的超时时间）
                    HttpClient httpClient = HttpClient.create()
                            .responseTimeout(Duration.ofSeconds(300))
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000); // 30秒连接超时

                    WebClient.Builder builder = WebClient.builder()
                            .baseUrl(baseUrl)
                            .clientConnector(
                                    new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));

                    if (providerType.requiresApiKey() && model.getApiKey() != null
                            && !model.getApiKey().trim().isEmpty()) {
                        builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + model.getApiKey());
                    }

                    webClient = builder.build();
                    logger.info("StreamingChatLanguageModel WebClient已创建，Provider: {}, URL: {}",
                            providerType.getValue(), baseUrl);
                }
                return webClient;
            }
        };
    }

    private boolean shouldSkipTrace(String traceSource) {
        return TRACE_SOURCE_USER_MEMORY_EXTRACTION.equalsIgnoreCase(traceSource);
    }

    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, QAModel qaModel) {
        // 从ThreadLocal获取图片数据
        List<com.github.app.dify.chat.req.ChatRequest.ImageData> imageDataList = getImageData();
        Map<String, Object> requestBody = new HashMap<>();

        // 检查是否支持多模态
        boolean supportsMultimodal = qaModel != null &&
                Boolean.TRUE.equals(qaModel.getSupportsMultimodal()) &&
                Boolean.TRUE.equals(qaModel.getSupportsVision());
        boolean hasImages = imageDataList != null && !imageDataList.isEmpty();
        boolean useMultimodal = supportsMultimodal && hasImages;

        // 转换消息格式
        List<Map<String, Object>> apiMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> apiMessage = new HashMap<>();

            if (message instanceof SystemMessage) {
                apiMessage.put("role", "system");
                apiMessage.put("content", ((SystemMessage) message).text());
            } else if (message instanceof UserMessage) {
                apiMessage.put("role", "user");

                // 如果是最后一条用户消息且支持多模态，构建多模态消息
                if (useMultimodal && message == messages.get(messages.size() - 1)) {
                    // 构建多模态消息格式（OpenAI Vision API格式）
                    List<Map<String, Object>> contentList = new ArrayList<>();

                    // 添加文本内容
                    String textContent = ((UserMessage) message).singleText();
                    if (textContent != null && !textContent.trim().isEmpty()) {
                        Map<String, Object> textItem = new HashMap<>();
                        textItem.put("type", "text");
                        textItem.put("text", textContent);
                        contentList.add(textItem);
                    }

                    // 添加图片内容
                    int imageCount = 0;
                    if (imageDataList != null) {
                        for (ChatRequest.ImageData imageData : imageDataList) {
                            Map<String, Object> imageItem = new HashMap<>();
                            imageItem.put("type", "image_url");
                            Map<String, String> imageUrl = new HashMap<>();
                            // 使用base64格式：data:image/png;base64,{base64_data}
                            imageUrl.put("url", "data:" + imageData.getMimeType() + ";base64," + imageData.getBase64());
                            imageItem.put("image_url", imageUrl);
                            contentList.add(imageItem);
                            imageCount++;
                        }
                    }

                    apiMessage.put("content", contentList);
                    logger.debug("构建多模态消息，包含 {} 张图片", imageCount);
                } else {
                    // 普通文本消息
                    apiMessage.put("content", ((UserMessage) message).singleText());
                }
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
        if (qaModel != null && qaModel.getModel() != null && !qaModel.getModel().trim().isEmpty()) {
            requestBody.put("model", qaModel.getModel());
        }

        return requestBody;
    }

    /**
     * 解析响应中的 token 使用信息
     * 支持格式：
     * 1. OpenAI格式：{"usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}}
     * 2. Ollama格式：{"prompt_eval_count":100,"eval_count":50}
     * @return int[3] = {inputTokens, outputTokens, totalTokens}
     */
    private int[] parseUsage(String responseJson) {
        int[] result = {0, 0, 0};
        if (responseJson == null || responseJson.trim().isEmpty()) {
            return result;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);
            
            // OpenAI 格式
            if (root.has("usage")) {
                com.fasterxml.jackson.databind.JsonNode usage = root.get("usage");
                if (usage.has("prompt_tokens")) {
                    result[0] = usage.get("prompt_tokens").asInt(0);
                }
                if (usage.has("completion_tokens")) {
                    result[1] = usage.get("completion_tokens").asInt(0);
                }
                if (usage.has("total_tokens")) {
                    result[2] = usage.get("total_tokens").asInt(0);
                } else {
                    result[2] = result[0] + result[1];
                }
                return result;
            }
            
            // Ollama 格式
            if (root.has("prompt_eval_count") || root.has("eval_count")) {
                result[0] = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt(0) : 0;
                result[1] = root.has("eval_count") ? root.get("eval_count").asInt(0) : 0;
                result[2] = result[0] + result[1];
                return result;
            }
            
        } catch (Exception e) {
            logger.debug("解析token使用信息失败: {}", e.getMessage());
        }
        return result;
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
            if (root.has("choices") && root.get("choices").isArray() && !root.get("choices").isEmpty()) {
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
        if (!root.has("choices") || !root.get("choices").isArray() || root.get("choices").isEmpty()) {
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
}
