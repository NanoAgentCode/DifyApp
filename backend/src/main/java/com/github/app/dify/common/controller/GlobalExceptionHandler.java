package com.github.app.dify.common.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.exception.UnauthorizedException;
import com.github.app.dify.common.resp.ApiResponse;
import com.github.app.dify.common.util.ErrorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTimeoutException;
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
    
    /**
     * 获取当前请求信息
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    /**
     * 构建标准化的错误响应
     */
    private <T> ResponseEntity<ApiResponse<T>> buildErrorResponse(String message, Integer code, HttpStatus httpStatus) {
        ApiResponse<T> response = ApiResponse.error(message, code);
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            response.setPath(request.getRequestURI());
        }
        return ResponseEntity.status(httpStatus).body(response);
    }
    
    /**
     * 记录异常日志
     */
    private void logException(String level, String message, Exception e, Object... args) {
        HttpServletRequest request = getCurrentRequest();
        String requestInfo = "";
        if (request != null) {
            Object userIdObj = request.getAttribute("userId");
            Object usernameObj = request.getAttribute("username");
            String userId = userIdObj != null ? String.valueOf(userIdObj) : "N/A";
            String username = usernameObj != null ? String.valueOf(usernameObj) : "N/A";
            requestInfo = String.format("[Method=%s] [URI=%s] [UserID=%s] [Username=%s] ", 
                    request.getMethod(), 
                    request.getRequestURI(),
                    userId,
                    username);
        }
        
        String logMessage = requestInfo + message;
        switch (level.toLowerCase()) {
            case "error":
                if (e != null && args.length > 0) {
                    logger.error(logMessage, args, e);
                } else if (e != null) {
                    logger.error(logMessage, e);
                } else if (args.length > 0) {
                    logger.error(logMessage, args);
                } else {
                    logger.error(logMessage);
                }
                break;
            case "warn":
                if (e != null && args.length > 0) {
                    logger.warn(logMessage, args, e);
                } else if (e != null) {
                    logger.warn(logMessage, e);
                } else if (args.length > 0) {
                    logger.warn(logMessage, args);
                } else {
                    logger.warn(logMessage);
                }
                break;
            default:
                if (e != null && args.length > 0) {
                    logger.info(logMessage, args, e);
                } else if (e != null) {
                    logger.info(logMessage, e);
                } else if (args.length > 0) {
                    logger.info(logMessage, args);
                } else {
                    logger.info(logMessage);
                }
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
        logException("warn", "业务异常: code={}, message={}", e, code, e.getMessage());
        return buildErrorResponse(message, code, HttpStatus.valueOf(httpStatus));
    }
    
    /**
     * 处理未找到异常
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NotFoundException e) {
        Integer code = e.getCode() != null ? e.getCode() : ErrorCode.NOT_FOUND;
        int httpStatus = ErrorCode.getHttpStatus(code);
        String message = ErrorUtil.toUserMessage(e.getMessage(), "资源不存在");
        logException("warn", "资源未找到: code={}, message={}", e, code, e.getMessage());
        return buildErrorResponse(message, code, HttpStatus.valueOf(httpStatus));
    }
    
    /**
     * 处理未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException e) {
        Integer code = e.getCode() != null ? e.getCode() : ErrorCode.UNAUTHORIZED;
        int httpStatus = ErrorCode.getHttpStatus(code);
        String message = ErrorUtil.toUserMessage(e.getMessage(), "未授权");
        logException("warn", "未授权: code={}, message={}", e, code, e.getMessage());
        return buildErrorResponse(message, code, HttpStatus.valueOf(httpStatus));
    }
    
    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbiddenException(ForbiddenException e) {
        Integer code = e.getCode() != null ? e.getCode() : ErrorCode.FORBIDDEN;
        int httpStatus = ErrorCode.getHttpStatus(code);
        String message = ErrorUtil.toUserMessage(e.getMessage(), "无权限访问");
        logException("warn", "禁止访问: code={}, message={}", e, code, e.getMessage());
        return buildErrorResponse(message, code, HttpStatus.valueOf(httpStatus));
    }
    
    /**
     * 处理Spring Security访问拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException e) {
        String message = ErrorUtil.toUserMessage(e.getMessage(), "无权限访问该资源");
        logException("warn", "访问拒绝: message={}", e, e.getMessage());
        return buildErrorResponse(message, ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e) {
        logException("warn", "参数验证失败", e);
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
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            response.setPath(request.getRequestURI());
        }
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        logException("warn", "参数绑定异常: message={}", e, e.getMessage());
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.contains("Name for argument")) {
            errorMessage = "参数绑定失败：请确保所有请求参数都有正确的注解（@RequestParam、@PathVariable等）。如果问题持续，请重新编译项目。";
        }
        String message = ErrorUtil.toUserMessage(errorMessage, "参数错误");
        return buildErrorResponse(message, ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logException("warn", "缺少请求参数: parameterName={}", e, e.getParameterName());
        String message = "缺少必需的请求参数: " + e.getParameterName();
        return buildErrorResponse(message, ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logException("warn", "参数类型不匹配: parameterName={}, requiredType={}", e, e.getName(), 
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型");
        String message = "参数类型不匹配: " + e.getName() + " 应该是 " + 
                (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型");
        return buildErrorResponse(message, ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        logException("warn", "请求方法不支持: method={}, supported={}", e, e.getMethod(), e.getSupportedMethods());
        String message = String.format("请求方法 '%s' 不支持，支持的请求方法: %s", 
                e.getMethod(), String.join(", ", e.getSupportedMethods()));
        return buildErrorResponse(message, ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED);
    }
    
    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        logException("warn", "媒体类型不支持: contentType={}", e, e.getContentType());
        String message = String.format("不支持的内容类型 '%s'，支持的类型: %s", 
                e.getContentType(), String.join(", ", e.getSupportedMediaTypes().stream()
                        .map(Object::toString).collect(Collectors.toList())));
        return buildErrorResponse(message, ErrorCode.BAD_REQUEST, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
    
    /**
     * 处理请求体不可读异常（包括JSON解析异常）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        logException("warn", "请求体不可读: message={}", e, e.getMessage());
        String message = "请求体格式错误或不可读";
        
        // 处理JSON解析异常
        Throwable cause = e.getCause();
        if (cause instanceof JsonParseException) {
            message = "JSON格式错误: " + cause.getMessage();
        } else if (cause instanceof JsonMappingException) {
            message = "JSON映射错误: " + cause.getMessage();
        } else if (cause != null) {
            message += ": " + cause.getMessage();
        }
        
        return buildErrorResponse(message, ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理路径变量缺失异常
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPathVariableException(MissingPathVariableException e) {
        logException("warn", "缺少路径变量: variableName={}", e, e.getVariableName());
        String message = "缺少必需的路径变量: " + e.getVariableName();
        return buildErrorResponse(message, ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理请求头缺失异常
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        logException("warn", "缺少请求头: headerName={}", e, e.getHeaderName());
        String message = "缺少必需的请求头: " + e.getHeaderName();
        return buildErrorResponse(message, ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(BindException e) {
        logException("warn", "参数绑定失败", e);
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
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            response.setPath(request.getRequestURI());
        }
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理约束违反异常（@Validated）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(ConstraintViolationException e) {
        logException("warn", "约束验证失败", e);
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
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            response.setPath(request.getRequestURI());
        }
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logException("warn", "文件上传大小超限: maxSize={}", e, e.getMaxUploadSize());
        String maxSizeMB = e.getMaxUploadSize() / (1024 * 1024) + "MB";
        return buildErrorResponse("上传文件大小超过限制（最大 " + maxSizeMB + "）", 
                ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
    }
    
    /**
     * 处理无处理器异常（404）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        logException("warn", "无处理器: method={}, url={}", e, e.getHttpMethod(), e.getRequestURL());
        String message = String.format("请求的资源不存在: %s %s", e.getHttpMethod(), e.getRequestURL());
        return buildErrorResponse(message, ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 处理异步请求超时异常
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        logException("warn", "异步请求超时", e);
        return buildErrorResponse("请求处理超时，请稍后重试或使用流式接口", 
                ErrorCode.REQUEST_TIMEOUT, HttpStatus.REQUEST_TIMEOUT);
    }
    
    /**
     * 处理数据库访问异常
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataAccessException(DataAccessException e) {
        logException("error", "数据库访问异常: message={}", e, e.getMessage());
        
        // 处理数据完整性违反异常（如外键约束、唯一约束等）
        if (e instanceof DataIntegrityViolationException) {
            DataIntegrityViolationException dive = (DataIntegrityViolationException) e;
            String message = "数据完整性约束违反";
            if (dive.getMessage() != null && dive.getMessage().contains("Duplicate entry")) {
                message = "数据已存在，不能重复添加";
                return buildErrorResponse(message, ErrorCode.DATA_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
            } else if (dive.getMessage() != null && dive.getMessage().contains("foreign key constraint")) {
                message = "数据关联关系错误，请检查关联数据是否存在";
            }
            return buildErrorResponse(message, ErrorCode.DATABASE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
        // 处理重复键异常
        if (e instanceof DuplicateKeyException) {
            return buildErrorResponse("数据已存在，不能重复添加", 
                    ErrorCode.DATA_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
        }
        
        return buildErrorResponse("数据库操作失败，请稍后重试", 
                ErrorCode.DATABASE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 处理SQL异常
     */
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiResponse<Object>> handleSQLException(SQLException e) {
        logException("error", "SQL异常: sqlState={}, errorCode={}, message={}", e, 
                e.getSQLState(), e.getErrorCode(), e.getMessage());
        
        // 处理SQL超时
        if (e instanceof SQLTimeoutException) {
            return buildErrorResponse("数据库操作超时，请稍后重试", 
                    ErrorCode.DATABASE_TIMEOUT, HttpStatus.REQUEST_TIMEOUT);
        }
        
        // 处理完整性约束违反
        if (e instanceof SQLIntegrityConstraintViolationException) {
            String message = "数据完整性约束违反";
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                message = "数据已存在，不能重复添加";
                return buildErrorResponse(message, ErrorCode.DATA_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
            }
            return buildErrorResponse(message, ErrorCode.DATABASE_ERROR, HttpStatus.BAD_REQUEST);
        }
        
        // 根据SQL状态码判断错误类型
        String sqlState = e.getSQLState();
        if (sqlState != null) {
            if (sqlState.startsWith("23")) { // 完整性约束违反
                return buildErrorResponse("数据完整性约束违反", 
                        ErrorCode.DATABASE_ERROR, HttpStatus.BAD_REQUEST);
            } else if (sqlState.startsWith("08")) { // 连接异常
                return buildErrorResponse("数据库连接失败，请稍后重试", 
                        ErrorCode.DATABASE_CONNECTION_ERROR, HttpStatus.SERVICE_UNAVAILABLE);
            } else if (sqlState.startsWith("40")) { // 事务回滚
                return buildErrorResponse("事务处理失败，请稍后重试", 
                        ErrorCode.DATABASE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        
        return buildErrorResponse("数据库操作失败，请稍后重试", 
                ErrorCode.DATABASE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        // 跳过已处理的异常类型
        if (e instanceof BusinessException || 
            e instanceof IllegalArgumentException ||
            e instanceof DataAccessException ||
            e instanceof SQLException) {
            throw e; // 重新抛出，让更具体的处理器处理
        }
        
        logException("error", "运行时异常: message={}, type={}", e, e.getMessage(), e.getClass().getSimpleName());
        return buildErrorResponse("系统繁忙，请稍后重试", 
                ErrorCode.SYSTEM_BUSY, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        logException("error", "系统异常: message={}, type={}", e, e.getMessage(), e.getClass().getSimpleName());
        
        // 处理网络连接异常
        if (e instanceof ConnectException) {
            return buildErrorResponse("无法连接到服务，请检查网络连接", 
                    ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        
        // 处理Socket超时异常
        if (e instanceof SocketTimeoutException) {
            return buildErrorResponse("请求超时，请稍后重试", 
                    ErrorCode.GATEWAY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        }
        
        // 处理JSON解析异常（如果未被HttpMessageNotReadableException捕获）
        if (e instanceof JsonParseException || e instanceof JsonMappingException) {
            return buildErrorResponse("JSON格式错误: " + e.getMessage(), 
                    ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        return buildErrorResponse("系统异常，请稍后重试", 
                ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
