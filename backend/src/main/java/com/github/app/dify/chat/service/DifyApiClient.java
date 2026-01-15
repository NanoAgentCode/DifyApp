package com.github.app.dify.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.system.config.DifyConfig;
import com.github.app.dify.chat.resp.DifyResponse;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.config.WebClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Dify API客户端
 */
@Service
public class DifyApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DifyApiClient.class);
    
    @Autowired
    private DifyConfig difyConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private WebClientConfig webClientConfig;
    
    /**
     * 获取WebClient实例（非流式）- 使用缓存优化
     */
    private WebClient getWebClient(String baseUrl) {
        String url = (baseUrl != null && !baseUrl.trim().isEmpty()) 
                ? baseUrl.trim() 
                : difyConfig.getDefaultBaseUrl();
        logger.info("使用Dify API Base URL（非流式）: {}", url);
        return webClientConfig.getWebClient(url);
    }
    
    /**
     * 获取WebClient实例（流式）- 使用缓存优化
     */
    private WebClient getStreamingWebClient(String baseUrl) {
        String url = (baseUrl != null && !baseUrl.trim().isEmpty()) 
                ? baseUrl.trim() 
                : difyConfig.getDefaultBaseUrl();
        logger.info("使用Dify API Base URL（流式）: {}", url);
        return webClientConfig.getStreamingWebClient(url);
    }
    
    /**
     * 调用Chat Flow API（非流式）
     */
    public Mono<DifyResponse> chat(String apiKey, String baseUrl, String query, String conversationId, 
                                    String userId, Map<String, Object> inputs, List<Map<String, Object>> files) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("API Key不能为空", ErrorCode.API_CONFIG_ERROR);
        }
        
        // 内部方法：执行实际的请求
        return executeChatRequest(apiKey, baseUrl, query, conversationId, userId, inputs, files, true);
    }
    
    /**
     * 执行非流式聊天请求（支持自动重试）
     */
    private Mono<DifyResponse> executeChatRequest(String apiKey, String baseUrl, String query, 
                                                   String conversationId, String userId, 
                                                   Map<String, Object> inputs, List<Map<String, Object>> files, boolean allowRetry) {
        WebClient webClient = getWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("query", query);
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            requestBody.put("conversation_id", conversationId);
        }
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        }
        if (files != null && !files.isEmpty()) {
            requestBody.put("files", files);
        }
        Map<String, Object> inputsPayload = inputs != null ? new java.util.HashMap<>(inputs) : new java.util.HashMap<>();
        if (files != null && !files.isEmpty() && !inputsPayload.containsKey("file")) {
            inputsPayload.put("file", files.get(0));
        }
        requestBody.put("inputs", inputsPayload);
        requestBody.put("response_mode", "blocking"); // Dify非流式响应模式
        requestBody.put("stream", false);
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Chat API, URL: {}, API Key: {}, conversationId: {}, 请求体: {}", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", conversationId, requestBody);
        
        // Dify API路径
        String apiPath = "/v1/chat-messages";
        
        // 对于非流式Chat响应，使用较长的超时时间（至少5分钟）
        long chatTimeout = Math.max(difyConfig.getTimeout(), 300000L); // 至少5分钟
        logger.info("Chat API超时时间设置为: {} 毫秒 ({} 分钟)", chatTimeout, chatTimeout / 60000);
        
        return webClient.post()
                .uri(apiPath)
                .header("Authorization", "Bearer " + apiKey.trim())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(chatTimeout))
                .map(response -> {
                    try {
                        return objectMapper.readValue(response, DifyResponse.class);
                    } catch (Exception e) {
                        logger.error("解析Dify响应失败", e);
                        throw new BusinessException("解析响应失败", ErrorCode.API_CALL_FAILED, e);
                    }
                })
                .doOnError(error -> {
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        logger.error("调用Dify Chat API失败: {} {}, 响应: {}", 
                            ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
                    } else {
                        logger.error("调用Dify Chat API失败", error);
                    }
                })
                .onErrorResume(error -> {
                    // 处理 conversationId 不存在的情况
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) {
                        org.springframework.web.reactive.function.client.WebClientResponseException.NotFound notFoundEx = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) error;
                        try {
                            String responseBody = notFoundEx.getResponseBodyAsString();
                            // 检查是否是 conversation 不存在的错误
                            if (responseBody != null && responseBody.contains("Conversation Not Exists")) {
                                logger.warn("Conversation ID 不存在: {}, 将清除 conversationId 并重试", conversationId);
                                // 如果允许重试且 conversationId 不为空，则清除 conversationId 并重试
                                if (allowRetry && conversationId != null && !conversationId.trim().isEmpty()) {
                                    logger.info("自动重试：清除无效的 conversationId 并重新发送请求");
                                    return executeChatRequest(apiKey, baseUrl, query, null, userId, inputs, files, false);
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("无法读取错误响应体", e);
                        }
                    }
                    // 其他错误直接返回
                    return Mono.error(error);
                });
    }
    
    /**
     * 调用Chat Flow API（流式）
     */
    public Flux<DifyResponse> chatStream(String apiKey, String baseUrl, String query, String conversationId,
                                          String userId, Map<String, Object> inputs, List<Map<String, Object>> files) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("API Key不能为空", ErrorCode.API_CONFIG_ERROR);
        }
        
        // 内部方法：执行实际的流式请求
        return executeChatStreamRequest(apiKey, baseUrl, query, conversationId, userId, inputs, files, true);
    }
    
    /**
     * 执行流式聊天请求（支持自动重试）
     */
    private Flux<DifyResponse> executeChatStreamRequest(String apiKey, String baseUrl, String query, 
                                                         String conversationId, String userId, 
                                                         Map<String, Object> inputs, List<Map<String, Object>> files, boolean allowRetry) {
        WebClient webClient = getStreamingWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("query", query);
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            requestBody.put("conversation_id", conversationId);
        }
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        }
        if (files != null && !files.isEmpty()) {
            requestBody.put("files", files);
        }
        Map<String, Object> inputsPayload = inputs != null ? new java.util.HashMap<>(inputs) : new java.util.HashMap<>();
        if (files != null && !files.isEmpty() && !inputsPayload.containsKey("file")) {
            inputsPayload.put("file", files.get(0));
        }
        requestBody.put("inputs", inputsPayload);
        requestBody.put("response_mode", "streaming"); // Dify流式响应模式
        requestBody.put("stream", true);
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Chat Stream API, URL: {}, API Key: {}, conversationId: {}, 请求体: {}", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", conversationId, requestBody);
        
        // Dify API路径
        String apiPath = "/v1/chat-messages";
        
        // 对于流式响应，使用更长的超时时间（10分钟）
        // 确保超时时间至少为10分钟，避免使用默认的30秒
        // 注意：这个超时时间用于监控元素间隔，如果10分钟内没有新元素到达，就会超时
        long streamTimeout = Math.max(difyConfig.getTimeout(), 600000L); // 至少10分钟
        logger.info("Chat Stream Flux超时时间设置为: {} 毫秒 ({} 分钟)", streamTimeout, streamTimeout / 60000);
        
        return webClient.post()
                .uri(apiPath)
                .header("Authorization", "Bearer " + apiKey.trim())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnSubscribe(subscription -> {
                    logger.info("开始订阅Dify Chat Stream API响应");
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
                    String buffer = state.getT1();
                    
                    // 累积新数据
                    String accumulated = buffer + chunk;
                    
                    // 提取完整的行
                    List<String> newLines = new ArrayList<>();
                    int lastNewline = accumulated.lastIndexOf('\n');
                    if (lastNewline >= 0) {
                        String completePart = accumulated.substring(0, lastNewline + 1);
                        String[] splitLines = completePart.split("\n");
                        for (String line : splitLines) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                newLines.add(trimmed);
                            }
                        }
                        // 保留不完整的部分
                        buffer = accumulated.substring(lastNewline + 1);
                    } else {
                        // 没有完整的行，保留全部
                        buffer = accumulated;
                    }
                    
                    return Tuples.of(buffer, newLines);
                })
                .skip(1) // 跳过初始状态
                .flatMap(state -> {
                    List<String> lines = state.getT2();
                    if (lines.isEmpty()) {
                        return Flux.<String>empty();
                    }
                    logger.debug("处理完整行: {} 行", lines.size());
                    return Flux.fromIterable(lines);
                })
                .flatMap(line -> {
                    try {
                        logger.info("收到SSE数据行: {}", line.length() > 200 ? line.substring(0, 200) + "..." : line);
                        if (line == null || line.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空行
                        }
                        // 处理SSE格式的数据
                        String jsonData = line;
                        if (jsonData.startsWith("data: ")) {
                            jsonData = jsonData.substring(6).trim();
                        }
                        if (jsonData.equals("[DONE]")) {
                            DifyResponse response = new DifyResponse();
                            response.setFinished(true);
                            logger.info("收到流式响应结束标记");
                            return Mono.just(response);
                        }
                        if (jsonData.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空数据
                        }
                        DifyResponse response = objectMapper.readValue(jsonData, DifyResponse.class);
                        logger.info("成功解析Dify响应: event={}, answer长度={}, finished={}", 
                                response.getEvent(), 
                                response.getAnswer() != null ? response.getAnswer().length() : 0,
                                response.getFinished());
                        return Mono.just(response);
                    } catch (Exception e) {
                        logger.warn("解析Dify流式响应失败，跳过该行: {}, 错误: {}", 
                                line.length() > 200 ? line.substring(0, 200) + "..." : line, e.getMessage());
                        logger.debug("解析错误详情", e);
                        // 跳过解析失败的行，返回空 Mono
                        return Mono.<DifyResponse>empty();
                    }
                })
                // 设置超时：如果10分钟内没有新元素到达，就会超时
                // 使用回退机制，超时时返回完成的响应标记
                .timeout(Duration.ofMillis(streamTimeout), Flux.defer(() -> {
                    logger.warn("Dify Chat Stream API响应超时（{}分钟内没有新元素），返回完成的响应标记", streamTimeout / 60000);
                    DifyResponse timeoutResponse = new DifyResponse();
                    timeoutResponse.setFinished(true);
                    return Flux.just(timeoutResponse);
                }))
                .doOnNext(response -> {
                    logger.info("发送Dify响应: event={}, finished={}", response.getEvent(), response.getFinished());
                })
                .doOnComplete(() -> {
                    logger.info("Dify Chat Stream API响应流完成");
                })
                .doOnCancel(() -> {
                    logger.warn("Dify Chat Stream API响应流被取消");
                })
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        logger.error("调用Dify Chat Stream API超时: 超时时间 {} 毫秒, URL: {}", streamTimeout, actualUrl);
                    } else if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        try {
                            String responseBody = ex.getResponseBodyAsString();
                            logger.error("调用Dify Chat Stream API失败: {} {}, URL: {}, 响应: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, responseBody);
                        } catch (Exception e) {
                            logger.error("调用Dify Chat Stream API失败: {} {}, URL: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl);
                        }
                    } else {
                        logger.error("调用Dify Chat Stream API失败, URL: " + actualUrl, error);
                    }
                })
                .onErrorResume(error -> {
                    // 处理超时错误，返回一个完成的响应而不是抛出异常
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        logger.warn("Dify Chat Stream API响应超时，返回超时响应");
                        DifyResponse timeoutResponse = new DifyResponse();
                        timeoutResponse.setFinished(true);
                        timeoutResponse.setEvent("error");
                        timeoutResponse.setAnswer("请求超时：Chat任务处理时间超过" + (streamTimeout / 60000) + "分钟。请稍后重试。");
                        return Flux.just(timeoutResponse);
                    }
                    // 处理 conversationId 不存在的情况
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) {
                        org.springframework.web.reactive.function.client.WebClientResponseException.NotFound notFoundEx = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) error;
                        try {
                            String responseBody = notFoundEx.getResponseBodyAsString();
                            // 检查是否是 conversation 不存在的错误
                            if (responseBody.contains("Conversation Not Exists")) {
                                logger.warn("Conversation ID 不存在: {}, 将清除 conversationId 并重试", conversationId);
                                // 如果允许重试且 conversationId 不为空，则清除 conversationId 并重试
                                if (allowRetry && conversationId != null && !conversationId.trim().isEmpty()) {
                                    logger.info("自动重试：清除无效的 conversationId 并重新发送请求");
                                    return executeChatStreamRequest(apiKey, baseUrl, query, null, userId, inputs, files, false);
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("无法读取错误响应体", e);
                        }
                    }
                    // 其他错误直接返回
                    return Flux.error(error);
                });
    }
    
    /**
     * 调用Workflow API（非流式）
     */
    public Mono<DifyResponse> workflow(String apiKey, String baseUrl, String userId, Map<String, Object> inputs, 
                                       List<Map<String, Object>> files, String traceId) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("API Key不能为空", ErrorCode.API_CONFIG_ERROR);
        }
        
        WebClient webClient = getWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        // user 是必需参数
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        } else {
            throw new BusinessException("用户ID不能为空", ErrorCode.BAD_REQUEST);
        }
        
        // 构建 inputs，将文件信息添加到 inputs 中
        Map<String, Object> finalInputs = inputs != null ? new java.util.HashMap<>(inputs) : new java.util.HashMap<>();
        
        // 如果有文件，将文件信息添加到 inputs 中，key 为 "file"
        if (files != null && !files.isEmpty()) {
            // 如果只有一个文件，直接添加到 inputs.file
            // 如果有多个文件，可能需要作为数组
            if (files.size() == 1) {
                finalInputs.put("file", files.get(0));
            } else {
                // 多个文件，作为数组
                finalInputs.put("file", files);
            }
            logger.info("将文件信息添加到 inputs.file，文件数量: {}", files.size());
        }
        
        // inputs 是必需参数，即使为空也要包含
        requestBody.put("inputs", finalInputs);
        // response_mode 是必需参数
        requestBody.put("response_mode", "blocking"); // Dify非流式响应模式
        
        // trace_id 是可选参数
        if (traceId != null && !traceId.trim().isEmpty()) {
            requestBody.put("trace_id", traceId.trim());
        }
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Workflow API, URL: {}, API Key: {}, 请求体: {}", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", requestBody);
        
        // Dify API路径
        String apiPath = "/v1/workflows/run";
        
        // 对于非流式Workflow响应，使用更长的超时时间（至少5分钟）
        // Workflow可能需要较长时间来处理复杂的任务
        long workflowTimeout = Math.max(difyConfig.getTimeout(), 300000L); // 至少5分钟
        logger.info("Workflow API超时时间设置为: {} 毫秒 ({} 分钟)", workflowTimeout, workflowTimeout / 60000);
        
        WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri(apiPath)
                .header("Authorization", "Bearer " + apiKey.trim());
        
        // trace_id 也可以通过 Header 传递（优先级最高）
        if (traceId != null && !traceId.trim().isEmpty()) {
            requestSpec = requestSpec.header("X-Trace-Id", traceId.trim());
        }
        
        return requestSpec.bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(workflowTimeout))
                .map(response -> {
                    try {
                        return objectMapper.readValue(response, DifyResponse.class);
                    } catch (Exception e) {
                        logger.error("解析Dify响应失败", e);
                        throw new BusinessException("解析响应失败", ErrorCode.API_CALL_FAILED, e);
                    }
                })
                .doOnError(error -> {
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        try {
                            String responseBody = ex.getResponseBodyAsString();
                            logger.error("调用Dify Workflow API失败: {} {}, URL: {}, API Key: {}, 请求体: {}, 响应: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, 
                                apiKey.substring(0, Math.min(10, apiKey.length())) + "...", 
                                requestBody, responseBody);
                            
                            // 检查是否是应用类型不匹配的错误
                            if (responseBody != null && responseBody.contains("not_workflow_app")) {
                                logger.error("⚠️ Dify API返回应用类型不匹配错误！");
                                logger.error("⚠️ 诊断信息：");
                                logger.error("⚠️   - API Key: {}...", apiKey.substring(0, Math.min(20, apiKey.length())));
                                logger.error("⚠️   - API Base URL: {}", actualUrl);
                                logger.error("⚠️   - 错误响应: {}", responseBody);
                                logger.error("⚠️ 解决方案：");
                                logger.error("⚠️   1. 登录Dify控制台，找到该API Key对应的应用");
                                logger.error("⚠️   2. 确认应用类型是否为 'Workflow'（工作流）");
                                logger.error("⚠️   3. 如果应用类型不是Workflow，请：");
                                logger.error("⚠️      a) 在Dify中创建新的Workflow应用，获取新的API Key");
                                logger.error("⚠️      b) 或者在本系统中更新应用的API Key为Workflow应用的API Key");
                            }
                        } catch (Exception e) {
                            logger.error("调用Dify Workflow API失败: {} {}, URL: {}, API Key: {}, 请求体: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, 
                                apiKey.substring(0, Math.min(10, apiKey.length())) + "...", requestBody);
                        }
                    } else {
                        logger.error("调用Dify Workflow API失败", error);
                    }
                })
                .onErrorMap(error -> {
                    // 处理参数缺失错误和应用类型不匹配错误，提供更友好的错误信息
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest) {
                        org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest badRequestEx = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest) error;
                        try {
                            String responseBody = badRequestEx.getResponseBodyAsString();
                            if (responseBody != null) {
                                // 检查是否是应用类型不匹配错误
                                if (responseBody.contains("not_workflow_app")) {
                                    String errorMessage = String.format(
                                        "Dify API返回错误：应用类型不匹配。\n" +
                                        "数据库中的应用类型已设置为Workflow，但API Key对应的Dify应用不是Workflow类型。\n" +
                                        "请检查：\n" +
                                        "1. 登录Dify控制台，找到API Key '%s...' 对应的应用\n" +
                                        "2. 确认该应用的类型是否为 'Workflow'（工作流）\n" +
                                        "3. 如果应用类型不是Workflow，请：\n" +
                                        "   a) 在Dify中创建新的Workflow应用，获取新的API Key\n" +
                                        "   b) 或者在本系统中更新应用的API Key为Workflow应用的API Key",
                                        apiKey.substring(0, Math.min(20, apiKey.length()))
                                    );
                                    return new BusinessException(errorMessage, ErrorCode.BAD_REQUEST, badRequestEx);
                                }
                                // 检查是否是参数缺失错误
                                if (responseBody.contains("is required in input form")) {
                                    // 提取缺失的参数名
                                    String missingParam = extractMissingParam(responseBody);
                                    String errorMessage = String.format("Workflow 缺少必需的输入参数: %s。请检查应用配置中的 inputs 字段，确保包含所有必需的参数。", missingParam);
                                    logger.warn("Workflow 参数缺失: {}, 当前输入参数: {}", missingParam, requestBody.get("inputs"));
                                    return new BusinessException(errorMessage, ErrorCode.BAD_REQUEST, badRequestEx);
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("无法解析错误响应体", e);
                        }
                    }
                    return error;
                });
    }
    
    /**
     * 从错误响应中提取缺失的参数名
     */
    private String extractMissingParam(String responseBody) {
        try {
            // 尝试解析 JSON 响应
            Map<String, Object> errorJson = objectMapper.readValue(responseBody, 
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            String message = (String) errorJson.get("message");
            if (message != null && message.contains("is required in input form")) {
                // 提取参数名，例如 "word is required in input form" -> "word"
                String[] parts = message.split(" is required");
                if (parts.length > 0) {
                    return parts[0].trim();
                }
            }
        } catch (Exception e) {
            // 如果解析失败，尝试正则匹配
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\" is required in input form");
            java.util.regex.Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            }
            // 尝试另一种格式
            pattern = java.util.regex.Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*) is required in input form");
            matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "未知参数";
    }
    
    /**
     * 调用Workflow API（流式）
     */
    public Flux<DifyResponse> workflowStream(String apiKey, String baseUrl, String userId, Map<String, Object> inputs,
                                             List<Map<String, Object>> files, String traceId) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("API Key不能为空", ErrorCode.API_CONFIG_ERROR);
        }
        
        WebClient webClient = getStreamingWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        // user 是必需参数
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        } else {
            throw new BusinessException("用户ID不能为空", ErrorCode.BAD_REQUEST);
        }
        
        // 构建 inputs，将文件信息添加到 inputs 中
        Map<String, Object> finalInputs = inputs != null ? new java.util.HashMap<>(inputs) : new java.util.HashMap<>();
        
        // 如果有文件，将文件信息添加到 inputs 中，key 为 "file"
        if (files != null && !files.isEmpty()) {
            // 如果只有一个文件，直接添加到 inputs.file
            // 如果有多个文件，可能需要作为数组
            if (files.size() == 1) {
                finalInputs.put("file", files.get(0));
            } else {
                // 多个文件，作为数组
                finalInputs.put("file", files);
            }
            logger.info("将文件信息添加到 inputs.file（流式），文件数量: {}", files.size());
        }
        
        // inputs 是必需参数，即使为空也要包含
        requestBody.put("inputs", finalInputs);
        // response_mode 是必需参数
        requestBody.put("response_mode", "streaming"); // Dify流式响应模式
        
        // trace_id 是可选参数
        if (traceId != null && !traceId.trim().isEmpty()) {
            requestBody.put("trace_id", traceId.trim());
        }
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Workflow Stream API, URL: {}, API Key: {}, 请求体: {}", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", requestBody);
        
        // 对于流式响应，使用更长的超时时间（10分钟）
        // 确保超时时间至少为10分钟，避免使用默认的30秒
        // 注意：这个超时时间用于监控元素间隔，如果10分钟内没有新元素到达，就会超时
        long streamTimeout = Math.max(difyConfig.getTimeout(), 600000L); // 至少10分钟
        logger.info("Workflow Stream Flux超时时间设置为: {} 毫秒 ({} 分钟)", streamTimeout, streamTimeout / 60000);
        
        WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri("/v1/workflows/run")
                .header("Authorization", "Bearer " + apiKey.trim())
                .accept(MediaType.TEXT_EVENT_STREAM);
        
        // trace_id 也可以通过 Header 传递（优先级最高）
        if (traceId != null && !traceId.trim().isEmpty()) {
            requestSpec = requestSpec.header("X-Trace-Id", traceId.trim());
        }
        
        return requestSpec.bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnSubscribe(subscription -> {
                    logger.info("开始订阅Dify Workflow Stream API响应，Flux超时时间: {} 毫秒", streamTimeout);
                })
                .doOnNext(dataBuffer -> {
                    logger.debug("收到数据块，大小: {} 字节", dataBuffer.readableByteCount());
                })
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .flatMap(chunk -> {
                    // 按行分割并处理每一行
                    List<String> lines = new ArrayList<>();
                    String[] splitLines = chunk.split("\n");
                    for (String line : splitLines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            lines.add(trimmed);
                        }
                    }
                    return Flux.fromIterable(lines);
                })
                .flatMap(line -> {
                    try {
                        logger.debug("收到SSE数据行: {}", line);
                        if (line == null || line.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空行
                        }
                        // 处理SSE格式的数据
                        String jsonData = line;
                        if (jsonData.startsWith("data: ")) {
                            jsonData = jsonData.substring(6).trim();
                        }
                        if (jsonData.equals("[DONE]")) {
                            DifyResponse response = new DifyResponse();
                            response.setFinished(true);
                            logger.info("收到流式响应结束标记");
                            return Mono.just(response);
                        }
                        if (jsonData.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空数据
                        }
                        DifyResponse response = objectMapper.readValue(jsonData, DifyResponse.class);
                        logger.debug("成功解析Dify响应: event={}, answer长度={}", 
                                response.getEvent(), 
                                response.getAnswer() != null ? response.getAnswer().length() : 0);
                        return Mono.just(response);
                    } catch (Exception e) {
                        logger.warn("解析Dify流式响应失败，跳过该行: {}, 错误: {}", line, e.getMessage());
                        return Mono.<DifyResponse>empty(); // 跳过解析失败的行
                    }
                })
                // 设置超时：如果10分钟内没有新元素到达，就会超时
                // 使用回退机制，超时时返回完成的响应标记，并包含错误信息
                .timeout(Duration.ofMillis(streamTimeout), Flux.defer(() -> {
                    logger.warn("Dify Workflow Stream API响应超时（{}分钟内没有新元素），返回超时响应", streamTimeout / 60000);
                    DifyResponse timeoutResponse = new DifyResponse();
                    timeoutResponse.setFinished(true);
                    timeoutResponse.setEvent("error");
                    timeoutResponse.setAnswer("请求超时：Workflow任务处理时间超过" + (streamTimeout / 60000) + "分钟。任务可能仍在后台运行，请稍后查看结果或使用更长的超时时间。");
                    return Flux.just(timeoutResponse);
                }))
                .doOnNext(response -> {
                    logger.debug("发送Dify响应: event={}, finished={}", response.getEvent(), response.getFinished());
                })
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        logger.error("调用Dify Workflow Stream API超时: 超时时间 {} 毫秒, URL: {}", streamTimeout, actualUrl);
                    } else if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        try {
                            String responseBody = ex.getResponseBodyAsString();
                            logger.error("调用Dify Workflow Stream API失败: {} {}, URL: {}, 请求体: {}, 响应: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, requestBody, responseBody);
                        } catch (Exception e) {
                            logger.error("调用Dify Workflow Stream API失败: {} {}, URL: {}, 请求体: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, requestBody);
                        }
                    } else {
                        logger.error("调用Dify Workflow Stream API失败, URL: " + actualUrl, error);
                    }
                })
                .onErrorResume(error -> {
                    // 处理超时错误，返回一个完成的响应而不是抛出异常
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        logger.warn("Dify Workflow Stream API响应超时，返回超时响应");
                        DifyResponse timeoutResponse = new DifyResponse();
                        timeoutResponse.setFinished(true);
                        timeoutResponse.setEvent("error");
                        timeoutResponse.setAnswer("请求超时：Workflow任务处理时间超过" + (streamTimeout / 60000) + "分钟。任务可能仍在后台运行，请稍后查看结果或使用更长的超时时间。");
                        return Flux.just(timeoutResponse);
                    }
                    // 处理参数缺失错误，提供更友好的错误信息
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest) {
                        org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest badRequestEx = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest) error;
                        try {
                            String responseBody = badRequestEx.getResponseBodyAsString();
                            if (responseBody != null && responseBody.contains("is required in input form")) {
                                // 提取缺失的参数名
                                String missingParam = extractMissingParam(responseBody);
                                String errorMessage = String.format("Workflow 缺少必需的输入参数: %s。请检查应用配置中的 inputs 字段，确保包含所有必需的参数。", missingParam);
                                logger.warn("Workflow 参数缺失: {}, 当前输入参数: {}", missingParam, requestBody.get("inputs"));
                                return Flux.error(new BusinessException(errorMessage, ErrorCode.BAD_REQUEST, badRequestEx));
                            }
                        } catch (Exception e) {
                            logger.warn("无法解析错误响应体", e);
                        }
                    }
                    return Flux.error(error);
                });
    }
    
    /**
     * 上传文件到Dify
     */
    public Mono<Map<String, Object>> uploadFile(String apiKey, String baseUrl, 
                                                 org.springframework.web.multipart.MultipartFile file, 
                                                 String userId) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Mono.error(new BusinessException("API Key不能为空", ErrorCode.API_CONFIG_ERROR));
        }
        
        if (file == null || file.isEmpty()) {
            return Mono.error(new BusinessException("文件不能为空", ErrorCode.BAD_REQUEST));
        }
        
        WebClient webClient = getWebClient(baseUrl);
        
        // 创建 MultipartBodyBuilder 来构建 multipart/form-data 请求
        org.springframework.util.LinkedMultiValueMap<String, Object> parts = 
            new org.springframework.util.LinkedMultiValueMap<>();
        
        // 添加文件
        try {
            org.springframework.core.io.ByteArrayResource fileResource = 
                new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename();
                    }
                };
            parts.add("file", fileResource);
        } catch (Exception e) {
            return Mono.error(new BusinessException("读取文件失败", ErrorCode.FILE_UPLOAD_FAILED, e));
        }
        
        // 添加 user 参数（可选）
        if (userId != null && !userId.trim().isEmpty()) {
            parts.add("user", userId);
        }
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) 
            ? baseUrl.trim() 
            : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify文件上传API, URL: {}, API Key: {}, 文件名: {}, 文件大小: {} 字节", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", 
                file.getOriginalFilename(), file.getSize());
        
        return webClient.post()
                .uri("/v1/files/upload")
                .header("Authorization", "Bearer " + apiKey.trim())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMinutes(5)) // 文件上传超时时间5分钟
                .map(response -> {
                    try {
                        // 解析响应为 Map
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = objectMapper.readValue(response, Map.class);
                        logger.info("文件上传成功，文件ID: {}", result.get("id"));
                        return result;
                    } catch (Exception e) {
                        logger.error("解析文件上传响应失败", e);
                        throw new BusinessException("解析文件上传响应失败", ErrorCode.API_CALL_FAILED, e);
                    }
                })
                .doOnError(error -> {
                    logger.error("文件上传失败", error);
                });
    }
}
