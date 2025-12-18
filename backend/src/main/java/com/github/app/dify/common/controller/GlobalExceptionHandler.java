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
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ApiResponse<Map<String, String>> response = ApiResponse.error("参数验证失败", 400);
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
        logger.error("运行时异常", e);
        String message = e.getMessage() != null ? e.getMessage() : "系统内部错误";
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }
    
    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        logger.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("系统内部错误", 500));
    }
}

