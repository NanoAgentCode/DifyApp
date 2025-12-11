package com.github.app.dify.appauth.service;

import com.github.app.dify.appauth.resp.UserAppVisibilityResp;
import java.util.List;

/**
 * 用户应用可见性服务接口
 */
public interface UserAppVisibilityService {
    
    /**
     * 获取用户的应用可见性列表
     * 
     * @param userId 用户ID
     * @return 应用可见性列表
     */
    List<UserAppVisibilityResp> getUserAppVisibilities(Long userId);
    
    /**
     * 更新用户对应用的可见性
     * 
     * @param userId 用户ID
     * @param appId 应用ID
     * @param visible 是否可见
     */
    void updateUserAppVisibility(Long userId, Long appId, Boolean visible);
}

