package com.github.app.dify.common.controller;

import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.UnauthorizedException;
import com.github.app.dify.common.resp.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * Controller基类，提供通用方法
 */
public abstract class BaseController {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    /**
     * 从请求中获取用户ID
     */
    protected Long getUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) {
            throw new UnauthorizedException("未登录或登录已过期，请重新登录", ErrorCode.UNAUTHORIZED);
        }
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }
        throw new UnauthorizedException("无法获取用户信息", ErrorCode.UNAUTHORIZED);
    }
    
    /**
     * 从请求中获取用户名
     */
    protected String getUsername(HttpServletRequest request) {
        Object usernameObj = request.getAttribute("username");
        if (usernameObj == null) {
            return null;
        }
        return usernameObj.toString();
    }

    /**
     * 从请求中获取用户角色（1=管理员，2=普通用户等），未设置时返回 null
     */
    protected Integer getRole(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        if (roleObj instanceof Integer) {
            return (Integer) roleObj;
        }
        return null;
    }
    
    /**
     * 成功响应（无数据）
     */
    protected <T> ResponseEntity<ApiResponse<T>> success() {
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * 成功响应（带数据）
     */
    protected <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }
    
    /**
     * 成功响应（带消息和数据）
     */
    protected <T> ResponseEntity<ApiResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }
    
    /**
     * 失败响应
     */
    protected <T> ResponseEntity<ApiResponse<T>> error(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }
    
    /**
     * 失败响应（带状态码）
     */
    protected <T> ResponseEntity<ApiResponse<T>> error(String message, int statusCode) {
        return ResponseEntity.status(statusCode).body(ApiResponse.error(message, statusCode));
    }
}
