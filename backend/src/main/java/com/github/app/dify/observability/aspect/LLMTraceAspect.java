package com.github.app.dify.observability.aspect;

import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.observability.annotation.LLMTrace;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * LLM追踪AOP切面
 * 自动提取会话ID并设置到ThreadLocal，用于LLM追踪日志记录
 */
@Aspect
@Component
public class LLMTraceAspect {

    private static final Logger logger = LoggerFactory.getLogger(LLMTraceAspect.class);

    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;

    /**
     * 定义切点：所有带@LLMTrace注解的方法
     */
    @Pointcut("@annotation(com.github.app.dify.observability.annotation.LLMTrace)")
    public void llmTracePointcut() {
    }

    /**
     * 环绕通知：提取会话ID并设置到ThreadLocal
     */
    @Around("llmTracePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LLMTrace llmTrace = method.getAnnotation(LLMTrace.class);

        if (llmTrace == null) {
            return joinPoint.proceed();
        }

        String traceSource = llmTrace.traceSource();
        String conversationIdParam = llmTrace.conversationIdParam();
        boolean extractFromReturn = llmTrace.extractFromReturn();

        // 设置TraceSource
        modelLanguageModelFactory.setTraceSource(traceSource);

        // 提取会话ID
        String conversationId = extractConversationId(joinPoint, method, conversationIdParam);
        if (conversationId != null) {
            modelLanguageModelFactory.setConversationId(conversationId);
            logger.debug("LLMTrace切面提取到会话ID: {}", conversationId);
        }

        try {
            // 执行目标方法
            Object result = joinPoint.proceed();

            // 如果方法执行后需要从返回值中提取会话ID
            if (extractFromReturn && conversationId == null && result != null) {
                String returnConversationId = extractConversationIdFromReturn(result);
                if (returnConversationId != null) {
                    modelLanguageModelFactory.setConversationId(returnConversationId);
                    logger.debug("LLMTrace切面从返回值提取到会话ID: {}", returnConversationId);
                }
            }

            return result;
        } finally {
            // 清理ThreadLocal
            modelLanguageModelFactory.clearTraceSource();
            modelLanguageModelFactory.clearConversationId();
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
     * 从对象中提取属性值
     */
    private String extractFromObject(Object obj, String propertyName) {
        if (obj == null) {
            return null;
        }

        try {
            // 尝试通过getter方法获取
            String getterName = "get" + capitalize(propertyName);
            Method getter = obj.getClass().getMethod(getterName);
            Object value = getter.invoke(obj);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Exception e) {
            // getter方法不存在或调用失败，尝试其他方式
            try {
                // 尝试直接访问字段
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(propertyName);
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Exception ex) {
                // 忽略
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

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
