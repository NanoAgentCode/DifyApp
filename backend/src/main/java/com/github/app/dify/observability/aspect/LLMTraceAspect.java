package com.github.app.dify.observability.aspect;

import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.observability.annotation.LLMTrace;
import com.github.app.dify.observability.config.ObservabilityConfig;
import com.github.app.dify.observability.util.ReflectionCache;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * LLM追踪AOP切面
 * 自动提取会话ID并设置到ThreadLocal，用于LLM追踪日志记录
 * 
 * 优化点：
 * 1. 使用反射缓存减少性能开销
 * 2. 异常隔离：监控失败不影响业务
 * 3. 支持动态开关
 */
@Aspect
@Component
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = true)
public class LLMTraceAspect {

    private static final Logger logger = LoggerFactory.getLogger(LLMTraceAspect.class);

    @Autowired(required = false)
    private ModelLanguageModelFactory modelLanguageModelFactory;

    @Autowired(required = false)
    private ObservabilityConfig observabilityConfig;

    /**
     * 定义切点：所有带@LLMTrace注解的方法
     */
    @Pointcut("@annotation(com.github.app.dify.observability.annotation.LLMTrace)")
    public void llmTracePointcut() {
    }

    /**
     * 环绕通知：提取会话ID并设置到ThreadLocal
     * 使用异常隔离，确保监控失败不影响业务
     */
    @Around("llmTracePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 检查是否启用监控
        if (observabilityConfig != null && !observabilityConfig.isEnabled()) {
            return joinPoint.proceed();
        }

        // 检查依赖是否注入
        if (modelLanguageModelFactory == null) {
            logger.warn("ModelLanguageModelFactory未注入，跳过监控");
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LLMTrace llmTrace = method.getAnnotation(LLMTrace.class);

        if (llmTrace == null) {
            return joinPoint.proceed();
        }

        String traceSource = llmTrace.traceSource();
        String conversationIdParam = llmTrace.conversationIdParam();
        boolean extractFromReturn = llmTrace.extractFromReturn();

        // 使用try-finally确保ThreadLocal被清理，即使出现异常
        try {
            // 设置TraceSource（异常隔离）
            try {
                modelLanguageModelFactory.setTraceSource(traceSource);
            } catch (Exception e) {
                logger.warn("设置TraceSource失败，继续执行业务逻辑", e);
            }

            // 提取会话ID（异常隔离）
            String conversationId = null;
            try {
                conversationId = extractConversationId(joinPoint, method, conversationIdParam);
                if (conversationId != null) {
                    modelLanguageModelFactory.setConversationId(conversationId);
                    if (logger.isTraceEnabled()) {
                        logger.trace("LLMTrace切面提取到会话ID: {}", conversationId);
                    }
                }
            } catch (Exception e) {
                logger.debug("提取会话ID失败，继续执行业务逻辑", e);
            }

            // 执行目标方法（这是关键，不能捕获异常）
            Object result = joinPoint.proceed();

            // 如果方法执行后需要从返回值中提取会话ID（异常隔离）
            if (extractFromReturn && conversationId == null && result != null) {
                try {
                    String returnConversationId = extractConversationIdFromReturn(result);
                    if (returnConversationId != null) {
                        modelLanguageModelFactory.setConversationId(returnConversationId);
                        if (logger.isTraceEnabled()) {
                            logger.trace("LLMTrace切面从返回值提取到会话ID: {}", returnConversationId);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("从返回值提取会话ID失败", e);
                }
            }

            return result;
        } finally {
            // 清理ThreadLocal（必须执行，避免内存泄漏）
            try {
                modelLanguageModelFactory.clearTraceSource();
                modelLanguageModelFactory.clearConversationId();
            } catch (Exception e) {
                logger.warn("清理ThreadLocal失败", e);
            }
        }
    }

    /**
     * 从方法参数中提取会话ID
     */
    private String extractConversationId(ProceedingJoinPoint joinPoint, Method method, String conversationIdParam) {
        try {
            Object[] args = joinPoint.getArgs();
            Parameter[] parameters = method.getParameters();

            // 如果指定了参数名，按指定方式提取
            if (conversationIdParam != null && !conversationIdParam.isEmpty()) {
                // 处理对象属性路径，如 "request.conversationId"
                if (conversationIdParam.contains(".")) {
                    String[] parts = conversationIdParam.split("\\.", 2);
                    String paramName = parts[0];
                    String propertyName = parts[1];

                    for (int i = 0; i < parameters.length; i++) {
                        if (parameters[i].getName().equals(paramName) && args[i] != null) {
                            return extractFromObject(args[i], propertyName);
                        }
                    }
                } else {
                    // 直接参数名
                    for (int i = 0; i < parameters.length; i++) {
                        if (parameters[i].getName().equals(conversationIdParam) && args[i] != null) {
                            return String.valueOf(args[i]);
                        }
                    }
                }
            }

            // 自动查找：遍历所有参数，查找包含getConversationId()方法的对象
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }

                // 如果参数本身就是String类型且看起来像ID
                if (arg instanceof String) {
                    String str = (String) arg;
                    if (!str.isEmpty() && (str.matches("\\d+") || str.length() > 10)) {
                        // 可能是会话ID，但不确定，继续查找
                    }
                }

                // 尝试从对象中提取conversationId
                String id = extractFromObject(arg, "conversationId");
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            }

        } catch (Exception e) {
            logger.warn("提取会话ID失败", e);
        }

        return null;
    }

    /**
     * 从对象中提取属性值（使用反射缓存优化性能）
     */
    private String extractFromObject(Object obj, String propertyName) {
        if (obj == null) {
            return null;
        }

        try {
            Object value = ReflectionCache.getPropertyValue(obj, propertyName);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Exception e) {
            // 忽略异常，避免影响业务
            if (logger.isTraceEnabled()) {
                logger.trace("提取属性值失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 从返回值中提取会话ID
     */
    private String extractConversationIdFromReturn(Object result) {
        if (result == null) {
            return null;
        }

        // 如果返回值是Flux（流式响应），无法直接提取
        if (result instanceof reactor.core.publisher.Flux) {
            return null;
        }

        return extractFromObject(result, "conversationId");
    }

}
