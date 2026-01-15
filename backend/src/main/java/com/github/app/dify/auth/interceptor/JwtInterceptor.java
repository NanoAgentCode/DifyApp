package com.github.app.dify.auth.interceptor;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.auth.util.JwtUtil;
import com.github.app.dify.common.exception.ErrorCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
/**
 * JWT拦截器
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtInterceptor.class);
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        // 允许OPTIONS请求通过（CORS预检请求）
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        
        // 获取Token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            token = request.getParameter("token");
        }
        
        // 验证Token
        if (token != null && !token.isEmpty()) {
            if (jwtUtil.validateToken(token)) {
                // 将用户信息存储到request中，供后续使用
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                Integer role = jwtUtil.getRoleFromToken(token);
                
                // 检查用户状态
                Optional<User> userOptional = userRepository.findById(userId);
                if (userOptional.isEmpty()) {
                    logger.warn("用户不存在 - 用户ID: {}", userId);
                    writeErrorResponse(response, ErrorCode.UNAUTHORIZED, ErrorCode.TOKEN_INVALID, "Token无效或已过期");
                    return false;
                }
                
                User user = userOptional.get();
                
                // 检查用户是否已删除
                if (user.getDeleted() != null && user.getDeleted() == 1) {
                    logger.warn("用户已被删除 - 用户ID: {}, 用户名: {}", userId, username);
                    writeErrorResponse(response, ErrorCode.UNAUTHORIZED, ErrorCode.TOKEN_INVALID, "Token无效或已过期");
                    return false;
                }
                
                // 检查用户状态：0-待审核，1-已激活，2-已禁用
                if (user.getStatus() == null || user.getStatus() == 0) {
                    logger.warn("用户待审核 - 用户ID: {}, 用户名: {}", userId, username);
                    writeErrorResponse(response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN, "账号待审核，请联系管理员");
                    return false;
                }
                
                if (user.getStatus() == 2) {
                    logger.warn("用户已被禁用 - 用户ID: {}, 用户名: {}", userId, username);
                    writeErrorResponse(response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
                    return false;
                }
                
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
                request.setAttribute("role", role);
                request.setAttribute("userStatus", user.getStatus());
                
                return true;
            } else {
                logger.warn("JWT Token无效: {}", token);
                writeErrorResponse(response, ErrorCode.UNAUTHORIZED, ErrorCode.TOKEN_INVALID, "Token无效或已过期");
                return false;
            }
        } else {
            logger.warn("请求缺少JWT Token: {}", request.getRequestURI());
            writeErrorResponse(response, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未授权，请先登录");
            return false;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int httpStatus, int code, String message) {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"success\":false,\"message\":\"" + escapeJson(message) + "\",\"data\":null,\"code\":" + code + "}");
        } catch (IOException e) {
            logger.error("写入响应失败", e);
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}

