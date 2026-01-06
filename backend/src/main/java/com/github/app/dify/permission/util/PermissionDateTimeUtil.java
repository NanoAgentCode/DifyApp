package com.github.app.dify.permission.util;

import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.permission.domain.UserAppVisibility;
import com.github.app.dify.permission.domain.UserDataSourceVisibility;
import com.github.app.dify.permission.domain.UserKnowledgeBaseVisibility;

import java.util.Date;

/**
 * 权限日期时间工具类
 * 提供权限相关实体的日期时间处理方法
 */
public class PermissionDateTimeUtil {
    
    /**
     * 设置用户知识库可见性的创建时间和更新时间
     * 适用于新建用户知识库可见性
     * 
     * @param visibility 用户知识库可见性实体
     */
    public static void setCreateAndUpdateTime(UserKnowledgeBaseVisibility visibility) {
        Date now = DateTimeUtil.now();
        visibility.setCreateTime(now);
        visibility.setUpdateTime(now);
    }
    
    /**
     * 设置用户知识库可见性的更新时间
     * 适用于更新用户知识库可见性
     * 
     * @param visibility 用户知识库可见性实体
     */
    public static void setUpdateTime(UserKnowledgeBaseVisibility visibility) {
        visibility.setUpdateTime(DateTimeUtil.now());
    }
    
    /**
     * 设置用户数据源可见性的创建时间和更新时间
     * 适用于新建用户数据源可见性
     * 
     * @param visibility 用户数据源可见性实体
     */
    public static void setCreateAndUpdateTime(UserDataSourceVisibility visibility) {
        Date now = DateTimeUtil.now();
        visibility.setCreateTime(now);
        visibility.setUpdateTime(now);
    }
    
    /**
     * 设置用户数据源可见性的更新时间
     * 适用于更新用户数据源可见性
     * 
     * @param visibility 用户数据源可见性实体
     */
    public static void setUpdateTime(UserDataSourceVisibility visibility) {
        visibility.setUpdateTime(DateTimeUtil.now());
    }
    
    /**
     * 设置用户应用可见性的创建时间和更新时间
     * 适用于新建用户应用可见性
     * 
     * @param visibility 用户应用可见性实体
     */
    public static void setCreateAndUpdateTime(UserAppVisibility visibility) {
        Date now = DateTimeUtil.now();
        visibility.setCreateTime(now);
        visibility.setUpdateTime(now);
    }
    
    /**
     * 设置用户应用可见性的更新时间
     * 适用于更新用户应用可见性
     * 
     * @param visibility 用户应用可见性实体
     */
    public static void setUpdateTime(UserAppVisibility visibility) {
        visibility.setUpdateTime(DateTimeUtil.now());
    }
}

