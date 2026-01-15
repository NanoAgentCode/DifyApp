package com.github.app.dify.common.controller;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.exception.UnauthorizedException;
import com.github.app.dify.common.resp.ApiResponse;
import com.github.app.dify.common.util.ErrorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理系统各类异常，返回标准化的错误响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String URI_KEY = "uri";
    private static final String METHOD_KEY = "method";
    private static final String USER_ID_KEY = "userId";
    
    /**
     * 构建日志上下文信息
     */
    private String buildLogContext(HttpServletRequest request) {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return String.format("[RequestID=%s] [Method=%s] [URI=%s] [UserID=%s]",
                requestId != null ? requestId : "N/A",
                request != null ? request.getMethod() : "N/A",
                request != null ? request.getRequestURI() : "N/A",
                MDC.get(USER_ID_KEY) != null ? MDC.get(USER_ID_KEY) : "N/A");
    }
    
    /**
     * 记录异常日志
     */
    private void logException(HttpServletRequest request, String level, String message, Throwable e) {
        String context = buildLogContext(request);
        String logMessage = context + " - " + message;
        
        switch (level.toLowerCase()) {
            case "error":
                logger.error(logMessage, e);
                break;
            case "warn":
                logger.warn(logMessage, e);
                break;
            default:
                logger.info(logMessage, e);
                break;
        }
    }
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        Integer code = e.getCode() != null ? e.getCode() : ErrorCode.BAD_REQUEST;
        int httpStatus = ErrorCode.getHttpStatus(code);
        String message = ErrorUtil.toUserMessage(e.getMessage(), "操作失败，请稍后重试");
        logger.warn("业务异常: code={}, message={}", code, e.getMessage());
        return ResponseEntity.status(httpStatus).body(ApiResponse.error(message, code));
    }
    
    /**
     * 处理未找到异常
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NotFoundException e) {
        Integer code = e.getCode() != null ? e.getCode() : ErrorCode.NOT_FOUND;
        int httpStatus = ErrorCode.getHttpStatus(code);
        String message = ErrorUtil.toUserMessage(e.getMessage(), "资源不存在");
        logger.warn("资源未找到: code={}, message={}", code, e.getMessage());
        return ResponseEntity.status(httpStatus).body(ApiResponse.error(message, code));
    }
    
    /**
     * 处理未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException e) {
        Integer code = e.getCode() != null ? e.getCode() : ErrorCode.UNAUTHORIZED;
        int httpStatus = ErrorCode.getHttpStatus(code);
        String message = ErrorUtil.toUserMessage(e.getMessage(), "未授权");
        logger.warn("未授权: code={}, message={}", code, e.getMessage());
        return ResponseEntity.status(httpStatus).body(ApiResponse.error(message, code));
    }
    
    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbiddenException(ForbiddenException e) {
        Integer code = e.getCode() != null ? e.getCode() : ErrorCode.FORBIDDEN;
        int httpStatus = ErrorCode.getHttpStatus(code);
        String message = ErrorUtil.toUserMessage(e.getMessage(), "无权限访问");
        logger.warn("禁止访问: code={}, message={}", code, e.getMessage());
        return ResponseEntity.status(httpStatus).body(ApiResponse.error(message, code));
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
        
        ApiResponse<Map<String, String>> response = ApiResponse.error(errorMessage.toString(), ErrorCode.BAD_REQUEST);
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
        String message = ErrorUtil.toUserMessage(errorMessage, "参数错误");
        return ResponseEntity.badRequest().body(ApiResponse.error(message, ErrorCode.BAD_REQUEST));
    }
    
    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logger.warn("缺少请求参数: {}", e.getParameterName());
        String message = "缺少必需的请求参数: " + e.getParameterName();
        return ResponseEntity.badRequest().body(ApiResponse.error(message, ErrorCode.BAD_REQUEST));
    }
    
    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logger.warn("参数类型不匹配: {}", e.getName());
        String message = "参数类型不匹配: " + e.getName() + " 应该是 " + 
                (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型");
        return ResponseEntity.badRequest().body(ApiResponse.error(message, ErrorCode.BAD_REQUEST));
    }
    
    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        logger.warn("请求方法不支持: method={}, supported={}", e.getMethod(), e.getSupportedMethods());
        String message = String.format("请求方法 '%s' 不支持，支持的请求方法: %s", 
                e.getMethod(), String.join(", ", e.getSupportedMethods()));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(message, ErrorCode.METHOD_NOT_ALLOWED));
    }
    
    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        logger.warn("媒体类型不支持: contentType={}", e.getContentType());
        String message = String.format("不支持的内容类型 '%s'，支持的类型: %s", 
                e.getContentType(), String.join(", ", e.getSupportedMediaTypes().stream()
                        .map(Object::toString).collect(Collectors.toList())));
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(message, ErrorCode.BAD_REQUEST));
    }
    
    /**
     * 处理请求体不可读异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        logger.warn("请求体不可读: {}", e.getMessage());
        String message = "请求体格式错误或不可读";
        if (e.getCause() != null) {
            message += ": " + e.getCause().getMessage();
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, ErrorCode.BAD_REQUEST));
    }
    
    /**
     * 处理路径变量缺失异常
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPathVariableException(MissingPathVariableException e) {
        logger.warn("缺少路径变量: {}", e.getVariableName());
        String message = "缺少必需的路径变量: " + e.getVariableName();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, ErrorCode.BAD_REQUEST));
    }
    
    /**
     * 处理请求头缺失异常
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        logger.warn("缺少请求头: {}", e.getHeaderName());
        String message = "缺少必需的请求头: " + e.getHeaderName();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, ErrorCode.BAD_REQUEST));
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(BindException e) {
        logger.warn("参数绑定失败", e);
        Map<String, String> errors = new HashMap<>();
        StringBuilder errorMessage = new StringBuilder("参数验证失败：");
        boolean first = true;
        
        for (FieldError error : e.getFieldErrors()) {
            String fieldName = error.getField();
            String message = error.getDefaultMessage();
            errors.put(fieldName, message);
            
            if (!first) {
                errorMessage.append("；");
            }
            errorMessage.append(fieldName).append(": ").append(message);
            first = false;
        }
        
        ApiResponse<Map<String, String>> response = ApiResponse.error(errorMessage.toString(), ErrorCode.BAD_REQUEST);
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理约束违反异常（@Validated）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(ConstraintViolationException e) {
        logger.warn("约束验证失败", e);
        Map<String, String> errors = new HashMap<>();
        StringBuilder errorMessage = new StringBuilder("参数验证失败：");
        boolean first = true;
        
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(path, message);
            
            if (!first) {
                errorMessage.append("；");
            }
            errorMessage.append(path).append(": ").append(message);
            first = false;
        }
        
        ApiResponse<Map<String, String>> response = ApiResponse.error(errorMessage.toString(), ErrorCode.BAD_REQUEST);
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logger.warn("文件上传大小超限: maxSize={}", e.getMaxUploadSize());
        String maxSizeMB = e.getMaxUploadSize() / (1024 * 1024) + "MB";
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("上传文件大小超过限制（最大 " + maxSizeMB + "）", ErrorCode.FILE_TOO_LARGE));
    }
    
    /**
     * 处理无处理器异常（404）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        logger.warn("无处理器: method={}, url={}", e.getHttpMethod(), e.getRequestURL());
        String message = String.format("请求的资源不存在: %s %s", e.getHttpMethod(), e.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(message, ErrorCode.NOT_FOUND));
    }
    
    /**
     * 处理异步请求超时异常
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        logger.warn("异步请求超时");
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error("请求处理超时，请稍后重试或使用流式接口", ErrorCode.REQUEST_TIMEOUT));
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("系统繁忙，请稍后重试", ErrorCode.SYSTEM_BUSY));
    }
    
    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        logger.error("系统异常: {} - 类型: {}", e.getMessage(), e.getClass().getSimpleName(), e);
        if (e instanceof java.net.ConnectException) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("无法连接到服务，请检查网络连接", ErrorCode.SERVICE_UNAVAILABLE));
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(ApiResponse.error("请求超时，请稍后重试", ErrorCode.GATEWAY_TIMEOUT));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("系统异常，请稍后重试", ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
