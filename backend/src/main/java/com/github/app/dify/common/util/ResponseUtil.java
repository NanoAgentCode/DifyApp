package com.github.app.dify.common.util;

import com.github.app.dify.common.resp.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应处理工具类
 * 提供标准化的 API 响应构建方法
 */
public class ResponseUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseUtil.class);
    
    /**
     * 构建成功响应
     * @param data 数据
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data);
    }
    
    /**
     * 构建成功响应（无数据）
     * @return ApiResponse
     */
    public static ApiResponse<Void> success() {
        return ApiResponse.success(null);
    }
    
    /**
     * 构建成功响应（带消息）
     * @param message 消息
     * @return ApiResponse
     */
    public static ApiResponse<Void> successWithMessage(String message) {
        return ApiResponse.success(message, null);
    }
    
    /**
     * 构建失败响应
     * @param message 错误消息
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.error(message);
    }
    
    /**
     * 构建失败响应（带默认消息）
     * @param exception 异常
     * @param defaultMessage 默认消息
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(Exception exception, String defaultMessage) {
        String message = exception.getMessage() != null ? exception.getMessage() : defaultMessage;
        logger.error("{}: {}", defaultMessage, message, exception);
        return ApiResponse.error(message);
    }
    
    /**
     * 构建问答响应
     * @param answer 答案
     * @param conversationId 对话ID
     * @return Map
     */
    public static Map<String, Object> buildQAResponse(String answer, String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("answer", answer);
        response.put("content", answer);  // 兼容两种字段名
        if (conversationId != null) {
            response.put("conversationId", conversationId);
        }
        return response;
    }
    
    /**
     * 构建问答响应（带来源）
     * @param answer 答案
     * @param conversationId 对话ID
     * @param sources 来源列表
     * @return Map
     */
    public static Map<String, Object> buildQAResponse(
            String answer,
            String conversationId,
            Object sources
    ) {
        Map<String, Object> response = buildQAResponse(answer, conversationId);
        if (sources != null) {
            response.put("sources", sources);
        }
        return response;
    }
    
    /**
     * 提取错误消息
     * @param exception 异常
     * @param defaultMessage 默认消息
     * @return 错误消息
     */
    public static String extractErrorMessage(Exception exception, String defaultMessage) {
        if (exception == null) {
            return defaultMessage;
        }
        
        String message = exception.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        
        return defaultMessage;
    }
    
    /**
     * 记录并构建错误响应
     * @param exception 异常
     * @param contextInfo 上下文信息
     * @param defaultMessage 默认错误消息
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> logAndBuildError(
            Exception exception,
            String contextInfo,
            String defaultMessage
    ) {
        String errorMessage = extractErrorMessage(exception, defaultMessage);
        logger.error("{} - 错误: {}", contextInfo, errorMessage, exception);
        return ApiResponse.error(errorMessage);
    }
}

