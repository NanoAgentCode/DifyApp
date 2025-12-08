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
import java.util.HashMap;
import java.util.Map;
/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
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