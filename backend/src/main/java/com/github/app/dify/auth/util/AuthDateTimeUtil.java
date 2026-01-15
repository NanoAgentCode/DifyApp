package com.github.app.dify.auth.util;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.common.util.EntityLifecycleUtil;

/**
 * 认证日期时间工具类
 * 提供认证相关实体的日期时间处理方法
 */
public class AuthDateTimeUtil {
    
    /**
     * 设置用户的创建时间和更新时间
     * 适用于新建用户
     * 
     * @param user 用户实体
     */
    public static void setCreateAndUpdateTime(User user) {
        EntityLifecycleUtil.setCreateAndUpdateTime(user);
    }
    
    /**
     * 设置用户的更新时间
     * 适用于更新用户
     * 
     * @param user 用户实体
     */
    public static void setUpdateTime(User user) {
        EntityLifecycleUtil.setUpdateTime(user);
    }
}
