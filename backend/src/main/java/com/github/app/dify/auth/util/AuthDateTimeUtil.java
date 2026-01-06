package com.github.app.dify.auth.util;

import com.github.app.dify.auth.domain.User;

import java.util.Date;

/**
 * 认证日期时间工具类
 * 提供统一的日期时间处理方法
 */
public class AuthDateTimeUtil {
    
    /**
     * 获取当前时间
     * 
     * @return 当前时间
     */
    public static Date now() {
        return new Date();
    }
    
    /**
     * 设置用户的创建时间和更新时间
     * 适用于新建用户
     * 
     * @param user 用户实体
     */
    public static void setCreateAndUpdateTime(User user) {
        Date now = now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
    }
    
    /**
     * 设置用户的更新时间
     * 适用于更新用户
     * 
     * @param user 用户实体
     */
    public static void setUpdateTime(User user) {
        user.setUpdateTime(now());
    }
}

