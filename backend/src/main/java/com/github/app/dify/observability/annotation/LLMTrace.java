package com.github.app.dify.observability.annotation;

import java.lang.annotation.*;

/**
 * LLM追踪注解
 * 用于标记需要记录LLM追踪日志的方法，自动提取会话ID并设置到ThreadLocal
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LLMTrace {

    /**
     * 追踪来源（如：Chat, Knowledge Base QA, Document Reader QA等）
     */
    String traceSource();

    /**
     * 会话ID参数名（从方法参数中提取）
     * 支持以下格式：
     * 1. 直接参数名，如 "conversationId" - 从方法参数中查找名为conversationId的参数
     * 2. 对象属性路径，如 "request.conversationId" - 从request对象的conversationId属性获取
     * 3. 空字符串 - 自动从方法参数中查找包含getConversationId()方法的对象
     */
    String conversationIdParam() default "";

    /**
     * 是否自动从返回值中提取会话ID
     * 如果为true，会尝试从返回值的conversationId属性中提取
     */
    boolean extractFromReturn() default false;
}
