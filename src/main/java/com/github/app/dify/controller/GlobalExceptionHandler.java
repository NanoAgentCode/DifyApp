package com.github.app.dify.controller;

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
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.error("参数绑定异常", e);
        Map<String, Object> response = new HashMap<>();
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.contains("Name for argument")) {
            errorMessage = "参数绑定失败：请确保所有请求参数都有正确的注解（@RequestParam、@PathVariable等）。如果问题持续，请重新编译项目。";
        }
        response.put("error", errorMessage);
        response.put("code", HttpStatus.BAD_REQUEST.value());
        response.put("type", "IllegalArgumentException");
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logger.error("缺少请求参数", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "缺少必需的请求参数: " + e.getParameterName());
        response.put("code", HttpStatus.BAD_REQUEST.value());
        response.put("type", "MissingServletRequestParameterException");
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logger.error("参数类型不匹配", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "参数类型不匹配: " + e.getName() + " 应该是 " + (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型"));
        response.put("code", HttpStatus.BAD_REQUEST.value());
        response.put("type", "MethodArgumentTypeMismatchException");
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", e.getMessage());
        response.put("code", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        response.put("error", "参数验证失败");
        response.put("errors", errors);
        response.put("code", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        logger.error("异步请求超时", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "请求处理超时，请稍后重试或使用流式接口");
        response.put("code", HttpStatus.REQUEST_TIMEOUT.value());
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("系统异常", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "系统内部错误");
        response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}