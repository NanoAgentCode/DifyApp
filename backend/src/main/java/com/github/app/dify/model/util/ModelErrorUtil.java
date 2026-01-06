package com.github.app.dify.model.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;

/**
 * 模型错误处理工具类
 * 提供统一的错误消息提取方法
 */
public class ModelErrorUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelErrorUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 从错误响应中提取友好的错误消息
     * 
     * @param errorBody 错误响应体
     * @param statusCode HTTP状态码
     * @return 友好的错误消息
     */
    public static String extractErrorMessage(String errorBody, HttpStatusCode statusCode) {
        if (errorBody == null || errorBody.trim().isEmpty()) {
            return "API返回错误: " + statusCode;
        }
        
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            
            // 尝试提取 error.message 字段（OpenAI 格式）
            if (root.has("error") && root.get("error").has("message")) {
                String message = root.get("error").get("message").asText();
                // 转换为更友好的中文提示
                if (message.contains("no available channels") || message.contains("no available")) {
                    return "模型不可用或模型名称不正确。请检查：\n" +
                           "1. 模型名称是否正确（注意大小写和完整版本号）\n" +
                           "2. 该模型是否在 API 提供商处可用\n" +
                           "3. API Key 是否有权限访问该模型\n" +
                           "错误详情: " + message;
                } else if (message.contains("invalid") || message.contains("Invalid")) {
                    return "请求参数无效: " + message;
                } else if (message.contains("unauthorized") || message.contains("Unauthorized")) {
                    return "API Key 无效或已过期: " + message;
                } else if (message.contains("not found") || message.contains("not_found")) {
                    return "模型不存在: " + message;
                }
                return message;
            }
            
            // 尝试提取 message 字段（通用格式）
            if (root.has("message")) {
                return root.get("message").asText();
            }
            
            // 尝试提取 error 字段（字符串格式）
            if (root.has("error") && root.get("error").isTextual()) {
                return root.get("error").asText();
            }
            
        } catch (Exception e) {
            logger.debug("解析错误响应失败，使用原始错误信息: {}", e.getMessage());
        }
        
        // 如果无法解析，返回原始错误信息（截取前500字符）
        return "API返回错误: " + statusCode + " - " + 
               (errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody);
    }
    
    /**
     * 从错误响应中提取友好的错误消息（仅错误体）
     * 
     * @param errorBody 错误响应体
     * @return 友好的错误消息
     */
    public static String extractErrorMessage(String errorBody) {
        if (errorBody == null || errorBody.trim().isEmpty()) {
            return "未知错误";
        }
        
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            
            // 尝试多种可能的错误字段
            if (root.has("error")) {
                JsonNode error = root.get("error");
                if (error.isTextual()) {
                    return error.asText();
                }
                if (error.has("message")) {
                    return error.get("message").asText();
                }
            }
            
            if (root.has("message")) {
                return root.get("message").asText();
            }
            
            if (root.has("error_message")) {
                return root.get("error_message").asText();
            }
            
            return errorBody;
        } catch (Exception e) {
            logger.debug("解析错误消息失败，返回原始错误体", e);
            return errorBody;
        }
    }
}

