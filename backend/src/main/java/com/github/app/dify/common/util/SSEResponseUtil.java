package com.github.app.dify.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * SSE 流式响应工具类
 * 统一处理 ServerSentEvent 的构建、错误处理和日志记录
 */
public class SSEResponseUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(SSEResponseUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构建 SSE 事件
     * @param data 数据对象
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> buildEvent(Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return ServerSentEvent.<String>builder()
                    .data(json)
                    .build();
        } catch (Exception e) {
            logger.error("序列化SSE数据失败", e);
            return buildErrorEvent("序列化数据失败");
        }
    }
    
    /**
     * 构建错误 SSE 事件
     * @param errorMessage 错误消息
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> buildErrorEvent(String errorMessage) {
        return buildErrorEvent(errorMessage, true);
    }
    
    /**
     * 构建错误 SSE 事件
     * @param errorMessage 错误消息
     * @param finished 是否标记为完成
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> buildErrorEvent(String errorMessage, boolean finished) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessage);
        errorResponse.put("finished", finished);
        return buildEvent(errorResponse);
    }
    
    /**
     * 构建完成标记事件
     * @param content 内容
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> buildFinishedEvent(String content) {
        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("finished", true);
        return buildEvent(response);
    }
    
    /**
     * 包装 Flux 流，添加统一的错误处理
     * @param flux 原始 Flux 流
     * @param errorMessagePrefix 错误消息前缀
     * @param contextInfo 上下文信息（用于日志）
     * @return 包装后的 Flux
     */
    public static Flux<ServerSentEvent<String>> wrapFluxWithErrorHandling(
            Flux<?> flux,
            String errorMessagePrefix,
            String contextInfo
    ) {
        return flux
                .map(SSEResponseUtil::buildEvent)
                .doOnError(error -> logger.error("{} - {}", errorMessagePrefix, contextInfo, error))
                .doOnComplete(() -> logger.debug("流式响应完成 - {}", contextInfo))
                .onErrorResume(error -> {
                    logger.error("{} - {}", errorMessagePrefix, contextInfo, error);
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "未知错误";
                    return Flux.just(buildErrorEvent(errorMessagePrefix + "：" + errorMsg));
                });
    }
    
    /**
     * 映射响应对象到 SSE 事件
     * @param <T> 响应类型
     * @param flux 原始 Flux 流
     * @param mapper 响应到Map的映射函数
     * @return Flux<ServerSentEvent<String>>
     */
    public static <T> Flux<ServerSentEvent<String>> mapToSSE(
            Flux<T> flux,
            Function<T, Map<String, Object>> mapper
    ) {
        return flux.map(response -> {
            try {
                Map<String, Object> responseMap = mapper.apply(response);
                return buildEvent(responseMap);
            } catch (Exception e) {
                logger.error("映射响应失败", e);
                return buildErrorEvent("处理响应失败");
            }
        });
    }
    
    /**
     * 创建问答响应的标准映射
     * @param answer 答案内容
     * @param conversationId 对话ID
     * @param finished 是否完成
     * @return Map
     */
    public static Map<String, Object> createQAResponseMap(String answer, String conversationId, Boolean finished) {
        Map<String, Object> map = new HashMap<>();
        if (answer != null) {
            map.put("content", answer);
            map.put("answer", answer);  // 兼容旧版本
        }
        if (conversationId != null) {
            map.put("conversationId", conversationId);
        }
        map.put("finished", finished != null ? finished : false);
        return map;
    }
    
    /**
     * 创建聊天响应的标准映射
     * @param answer 答案内容
     * @param conversationId 对话ID
     * @param event 事件类型
     * @param finished 是否完成
     * @return Map
     */
    public static Map<String, Object> createChatResponseMap(
            String answer,
            String conversationId,
            String event,
            Boolean finished
    ) {
        Map<String, Object> map = new HashMap<>();
        if (answer != null) {
            map.put("answer", answer);
        }
        if (conversationId != null) {
            map.put("conversation_id", conversationId);
            map.put("conversationId", conversationId);  // 兼容两种格式
        }
        if (event != null) {
            map.put("event", event);
        }
        map.put("finished", finished != null ? finished : false);
        return map;
    }
    
    /**
     * 处理异常并返回错误事件流
     * @param exception 异常
     * @param contextInfo 上下文信息
     * @return Flux<ServerSentEvent<String>>
     */
    public static Flux<ServerSentEvent<String>> handleException(Exception exception, String contextInfo) {
        logger.error("处理请求失败 - {}", contextInfo, exception);
        String errorMsg = exception.getMessage() != null ? exception.getMessage() : "请求处理失败";
        return Flux.just(buildErrorEvent(errorMsg));
    }
}

