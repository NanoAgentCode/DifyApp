package com.github.app.dify.common.controller;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.exception.UnauthorizedException;
import com.github.app.dify.common.resp.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: {}", e.getMessage());
        ApiResponse<Object> response = ApiResponse.error(e.getMessage(), e.getCode());
        return ResponseEntity.status(e.getCode()).body(response);
    }
    
    /**
     * 处理未找到异常
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NotFoundException e) {
        logger.warn("资源未找到: {}", e.getMessage());
        return ResponseEntity.status(404).body(ApiResponse.notFound(e.getMessage()));
    }
    
    /**
     * 处理未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException e) {
        logger.warn("未授权: {}", e.getMessage());
        return ResponseEntity.status(401).body(ApiResponse.unauthorized(e.getMessage()));
    }
    
    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(com.github.app.dify.common.exception.ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbiddenException(com.github.app.dify.common.exception.ForbiddenException e) {
        logger.warn("禁止访问: {}", e.getMessage());
        return ResponseEntity.status(403).body(ApiResponse.error(e.getMessage(), 403));
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e) {
        logger.warn("参数验证失败", e);
        Map<String, String> errors = new HashMap<>();
        StringBuilder errorMessage = new StringBuilder("参数验证失败：");
        boolean first = true;
        
        for (org.springframework.validation.ObjectError error : e.getBindingResult().getAllErrors()) {
            String fieldName = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
            String message = error.getDefaultMessage();
            errors.put(fieldName, message);
            
            if (!first) {
                errorMessage.append("；");
            }
            errorMessage.append(fieldName).append(": ").append(message);
            first = false;
        }
        
        ApiResponse<Map<String, String>> response = ApiResponse.error(errorMessage.toString(), 400);
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.error("参数绑定异常", e);
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.contains("Name for argument")) {
            errorMessage = "参数绑定失败：请确保所有请求参数都有正确的注解（@RequestParam、@PathVariable等）。如果问题持续，请重新编译项目。";
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(errorMessage));
    }
    
    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logger.warn("缺少请求参数: {}", e.getParameterName());
        String message = "缺少必需的请求参数: " + e.getParameterName();
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }
    
    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logger.warn("参数类型不匹配: {}", e.getName());
        String message = "参数类型不匹配: " + e.getName() + " 应该是 " + 
                (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型");
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }
    
    /**
     * 处理异步请求超时异常
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        logger.warn("异步请求超时");
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error("请求处理超时，请稍后重试或使用流式接口", 408));
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage(), e);
        
        // 使用统一的用户友好错误消息，不泄露技术细节
        String message = sanitizeErrorMessage(e.getMessage());
        
        return ResponseEntity.badRequest().body(ApiResponse.error(message, 400));
    }
    
    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        logger.error("系统异常: {} - 类型: {}", e.getMessage(), e.getClass().getSimpleName(), e);
        
        // 统一使用用户友好的错误消息，不泄露技术细节
        String message = sanitizeErrorMessage(e.getMessage());
        
        // 根据异常类型提供更具体的错误信息（仅非敏感信息）
        if (e instanceof java.net.ConnectException) {
            message = "无法连接到服务，请检查网络连接";
        } else if (e instanceof java.net.SocketTimeoutException) {
            message = "请求超时，请稍后重试";
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(message, 500));
    }
    
    /**
     * 清理和脱敏错误消息
     * 移除敏感的技术细节，返回用户友好的消息
     * 
     * @param originalMessage 原始错误消息
     * @return 清理后的用户友好消息
     */
    private String sanitizeErrorMessage(String originalMessage) {
        if (originalMessage == null || originalMessage.trim().isEmpty()) {
            return "操作失败，请稍后重试";
        }
        
        String message = originalMessage.trim();
        
        // 移除可能包含敏感信息的模式
        String[] sensitivePatterns = {
            // SQL 相关（可能泄露表结构、字段名等）
            "SQL", "SQLState", "ErrorCode", "syntax error", "constraint",
            "column", "table", "foreign key", "duplicate key",
            
            // 数据库连接信息（可能泄露连接字符串、密码等）
            "jdbc:", "postgresql://", "mysql://", "Connection refused",
            
            // 堆栈信息（绝对不能泄露）
            "at ", "Caused by:", "Exception:", "java.lang.",
            "org.springframework", "org.hibernate", "org.postgresql",
            
            // 文件路径信息（可能泄露服务器路径）
            "/", "\\", ".java", ".class",
            
            // 其他技术细节
            "classpath:", "jar:file:", "nested exception"
        };
        
        for (String pattern : sensitivePatterns) {
            if (message.contains(pattern)) {
                // 检测到敏感信息，返回通用错误消息
                logger.warn("检测到错误消息包含敏感信息，已过滤: {}", 
                    message.substring(0, Math.min(100, message.length())));
                return "操作失败，请稍后重试或联系管理员";
            }
        }
        
        // 检查消息是否过于技术性
        if (isTechnicalMessage(message)) {
            logger.warn("错误消息过于技术性，已替换为通用消息");
            return "操作失败，请稍后重试";
        }
        
        return message;
    }
    
    /**
     * 判断错误消息是否过于技术性
     * 
     * @param message 错误消息
     * @return true 如果消息包含过多技术细节
     */
    private boolean isTechnicalMessage(String message) {
        int technicalIndicators = 0;
        
        String[] technicalPatterns = {
            "Exception", "Error", "failed", "timeout", "null",
            "not found", "illegal", "invalid", "constraint",
            "SQL", "database", "connection", "network"
        };
        
        for (String pattern : technicalPatterns) {
            if (message.toLowerCase().contains(pattern.toLowerCase())) {
                technicalIndicators++;
            }
        }
        
        // 如果包含3个或以上技术关键词，认为是技术性消息
        return technicalIndicators >= 3;
    }
}
