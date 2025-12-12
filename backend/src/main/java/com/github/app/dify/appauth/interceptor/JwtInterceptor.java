package com.github.app.dify.appauth.interceptor;

import com.github.app.dify.appauth.domain.User;
import com.github.app.dify.appauth.repository.UserRepository;
import com.github.app.dify.appauth.util.JwtUtil;
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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
                if (!userOptional.isPresent()) {
                    logger.warn("用户不存在 - 用户ID: {}", userId);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    try {
                        response.getWriter().write("{\"error\":\"用户不存在\",\"code\":401}");
                    } catch (IOException e) {
                        logger.error("写入响应失败", e);
                    }
                    return false;
                }
                
                User user = userOptional.get();
                
                // 检查用户是否已删除
                if (user.getDeleted() != null && user.getDeleted() == 1) {
                    logger.warn("用户已被删除 - 用户ID: {}, 用户名: {}", userId, username);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    try {
                        response.getWriter().write("{\"error\":\"用户已被删除\",\"code\":401}");
                    } catch (IOException e) {
                        logger.error("写入响应失败", e);
                    }
                    return false;
                }
                
                // 检查用户状态：0-待审核，1-已激活，2-已禁用
                if (user.getStatus() == null || user.getStatus() == 0) {
                    logger.warn("用户待审核 - 用户ID: {}, 用户名: {}", userId, username);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    try {
                        response.getWriter().write("{\"error\":\"账号待审核，请联系管理员\",\"code\":403}");
                    } catch (IOException e) {
                        logger.error("写入响应失败", e);
                    }
                    return false;
                }
                
                if (user.getStatus() == 2) {
                    logger.warn("用户已被禁用 - 用户ID: {}, 用户名: {}", userId, username);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    try {
                        response.getWriter().write("{\"error\":\"账号已被禁用，请联系管理员\",\"code\":403}");
                    } catch (IOException e) {
                        logger.error("写入响应失败", e);
                    }
                    return false;
                }
                
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
                request.setAttribute("role", role);
                request.setAttribute("userStatus", user.getStatus());
                
                return true;
            } else {
                logger.warn("JWT Token无效: {}", token);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"error\":\"Token无效或已过期\",\"code\":401}");
                } catch (IOException e) {
                    logger.error("写入响应失败", e);
                }
                return false;
            }
        } else {
            logger.warn("请求缺少JWT Token: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"error\":\"未授权，请先登录\",\"code\":401}");
            } catch (IOException e) {
                logger.error("写入响应失败", e);
            }
            return false;
        }
    }
}

