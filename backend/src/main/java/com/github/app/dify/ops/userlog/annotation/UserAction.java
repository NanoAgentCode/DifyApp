package com.github.app.dify.ops.userlog.annotation;

import java.lang.annotation.*;

/**
 * 用户行为日志注解
 * 用于标记需要记录的关键操作
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserAction {

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 操作类型
     */
    String actionType() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数
     */
    boolean logParams() default true;
}
