package com.github.app.dify.permission.util;

import com.github.app.dify.permission.domain.UserAppVisibility;
import com.github.app.dify.permission.domain.UserDataSourceVisibility;
import com.github.app.dify.permission.domain.UserKnowledgeBaseVisibility;
import com.github.app.dify.permission.resp.UserAppVisibilityResp;
import com.github.app.dify.permission.resp.UserDataSourceVisibilityResp;
import com.github.app.dify.permission.resp.UserKnowledgeBaseVisibilityResp;
import org.springframework.beans.BeanUtils;

/**
 * 权限实体转换工具类
 * 提供权限相关实体的转换方法
 */
public class PermissionConverterUtil {
    
    /**
     * 将 UserKnowledgeBaseVisibility 转换为 UserKnowledgeBaseVisibilityResp
     * 
     * @param visibility 用户知识库可见性实体
     * @return 用户知识库可见性响应对象
     */
    public static UserKnowledgeBaseVisibilityResp convertToResp(UserKnowledgeBaseVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        
        UserKnowledgeBaseVisibilityResp resp = new UserKnowledgeBaseVisibilityResp();
        BeanUtils.copyProperties(visibility, resp);
        return resp;
    }
    
    /**
     * 将 UserDataSourceVisibility 转换为 UserDataSourceVisibilityResp
     * 
     * @param visibility 用户数据源可见性实体
     * @return 用户数据源可见性响应对象
     */
    public static UserDataSourceVisibilityResp convertToResp(UserDataSourceVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        
        UserDataSourceVisibilityResp resp = new UserDataSourceVisibilityResp();
        BeanUtils.copyProperties(visibility, resp);
        return resp;
    }
    
    /**
     * 将 UserAppVisibility 转换为 UserAppVisibilityResp
     * 
     * @param visibility 用户应用可见性实体
     * @return 用户应用可见性响应对象
     */
    public static UserAppVisibilityResp convertToResp(UserAppVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        
        UserAppVisibilityResp resp = new UserAppVisibilityResp();
        BeanUtils.copyProperties(visibility, resp);
        return resp;
    }
}

