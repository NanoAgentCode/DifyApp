package com.github.app.dify.userlog.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.auth.util.JwtUtil;
import com.github.app.dify.userlog.annotation.UserAction;
import com.github.app.dify.userlog.domain.UserActionLog;
import com.github.app.dify.userlog.service.UserActionLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户行为日志AOP切面
 */
@Aspect
@Component
public class UserActionAspect {

    private static final Logger logger = LoggerFactory.getLogger(UserActionAspect.class);

    @Autowired
    private UserActionLogService userActionLogService;

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 定义切点：所有带@UserAction注解的方法
     */
    @Pointcut("@annotation(com.github.app.dify.userlog.annotation.UserAction)")
    public void userActionPointcut() {
    }

    /**
     * 环绕通知：记录用户操作日志
     */
    @Around("userActionPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        UserActionLog log = new UserActionLog();
        Object result = null;

        try {
            // 获取Request对象
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            UserAction userAction = method.getAnnotation(UserAction.class);

            // 填充基本信息
            log.setModule(userAction.module());
            log.setActionType(userAction.actionType());
            log.setDescription(userAction.description());
            log.setCreateTime(LocalDateTime.now());

            // 填充请求信息（不需要用户信息）
            if (request != null) {
                fillRequestInfo(request, log);
                // 记录请求参数
                if (userAction.logParams()) {
                    String params = getRequestParams(joinPoint, request);
                    log.setRequestParams(params);
                }
            }

            // 执行目标方法
            result = joinPoint.proceed();
            
            // 执行目标方法后再次获取用户信息（Controller可能已经设置了）
            if (request != null) {
                fillUserInfo(request, log);
            }
            
            // 记录成功信息
            log.setResult("SUCCESS");
            return result;

        } catch (Throwable e) {
            // 即使失败也尝试获取用户信息（Controller可能已经设置了）
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
            if (request != null) {
                fillUserInfo(request, log);
            }
            
            // 记录失败信息
            log.setResult("FAILURE");
            log.setErrorMsg(e.getMessage() != null ? 
                (e.getMessage().length() > 2000 ? e.getMessage().substring(0, 2000) : e.getMessage()) 
                : e.getClass().getName());
            throw e;

        } finally {
            // 计算执行时长
            long executionTime = System.currentTimeMillis() - startTime;
            log.setExecutionTime(executionTime);

            // 异步保存日志
            try {
                userActionLogService.saveLog(log);
            } catch (Exception e) {
                logger.error("保存用户行为日志失败", e);
            }
        }
    }

    /**
     * 填充用户信息
     */
    private void fillUserInfo(HttpServletRequest request, UserActionLog log) {
        try {
            // 先从request attribute中获取
            Object userIdObj = request.getAttribute("userId");
            Object usernameObj = request.getAttribute("username");

            logger.info("AOP切面获取用户信息 - userId: {}, username: {}", userIdObj, usernameObj);

            if (userIdObj != null) {
                if (userIdObj instanceof Long) {
                    log.setUserId((Long) userIdObj);
                } else if (userIdObj instanceof Integer) {
                    log.setUserId(((Integer) userIdObj).longValue());
                }
            }

            if (usernameObj != null) {
                log.setUsername(usernameObj.toString());
            }

            // 如果attribute中没有，尝试从Token中获取
            if (log.getUserId() == null) {
                String authorization = request.getHeader("Authorization");
                if (authorization != null && authorization.startsWith("Bearer ")) {
                    String token = authorization.substring(7);
                    try {
                        Long userId = jwtUtil.getUserIdFromToken(token);
                        String username = jwtUtil.getUsernameFromToken(token);
                        log.setUserId(userId);
                        log.setUsername(username);
                        logger.info("从Token获取用户信息 - userId: {}, username: {}", userId, username);
                    } catch (Exception e) {
                        // Token解析失败，忽略
                        logger.warn("Token解析失败", e);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("获取用户信息失败", e);
        }
    }

    /**
     * 填充请求信息
     */
    private void fillRequestInfo(HttpServletRequest request, UserActionLog log) {
        log.setMethod(request.getMethod());
        log.setRequestPath(request.getRequestURI());
        log.setIpAddress(getIpAddress(request));
        log.setUserAgent(request.getHeader("User-Agent"));
    }

    /**
     * 获取请求参数
     */
    private String getRequestParams(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        try {
            Map<String, Object> params = new HashMap<>();

            // 获取方法参数
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < parameterNames.length; i++) {
                Object arg = args[i];
                // 过滤掉不需要记录的参数类型
                if (arg != null && !isFilterType(arg)) {
                    params.put(parameterNames[i], arg);
                }
            }

            // 获取查询参数
            Map<String, String[]> queryParams = request.getParameterMap();
            if (!queryParams.isEmpty()) {
                params.put("queryParams", queryParams);
            }

            String jsonParams = objectMapper.writeValueAsString(params);
            // 限制参数长度
            return jsonParams.length() > 5000 ? jsonParams.substring(0, 5000) + "..." : jsonParams;
        } catch (Exception e) {
            logger.warn("序列化请求参数失败", e);
            return "";
        }
    }

    /**
     * 判断是否为需要过滤的参数类型
     */
    private boolean isFilterType(Object obj) {
        return obj instanceof HttpServletRequest 
            || obj instanceof jakarta.servlet.http.HttpServletResponse
            || obj instanceof org.springframework.web.multipart.MultipartFile;
    }

    /**
     * 获取真实IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
