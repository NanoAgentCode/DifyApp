package com.github.app.dify.auth.util;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.resp.UserResp;

/**
 * 认证实体转换工具类
 * 提供认证相关实体的转换方法
 */
public class AuthConverterUtil {
    
    /**
     * 将 User 转换为 UserResp
     * 
     * @param user 用户实体
     * @return 用户响应对象
     */
    public static UserResp convertToResp(User user) {
        if (user == null) {
            return null;
        }
        
        UserResp resp = new UserResp();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setRole(user.getRole());
        resp.setStatus(user.getStatus());
        resp.setCreateTime(user.getCreateTime());
        resp.setUpdateTime(user.getUpdateTime());
        return resp;
    }
}

