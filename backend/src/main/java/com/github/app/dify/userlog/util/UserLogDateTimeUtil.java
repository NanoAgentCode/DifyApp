package com.github.app.dify.userlog.util;

import com.github.app.dify.userlog.domain.UserActionLog;

import java.time.LocalDateTime;

/**
 * 用户日志日期时间工具类
 * 提供用户日志相关实体的日期时间处理方法
 */
public class UserLogDateTimeUtil {
    
    /**
     * 设置用户行为日志的创建时间
     * 适用于新建用户行为日志
     * 
     * @param log 用户行为日志实体
     */
    public static void setCreateTime(UserActionLog log) {
        log.setCreateTime(LocalDateTime.now());
    }
}