package com.github.app.dify.service;

import com.github.app.dify.resp.UserAppVisibilityResp;
import java.util.List;
/**
 * 用户应用可见性服务接口
 */
public interface UserAppVisibilityService {
    
    /**
     * 获取用户的所有应用可见性列表
     * 如果用户没有设置可见性，默认所有应用都可见
     */
    List<UserAppVisibilityResp> getUserAppVisibilities(Long userId);
    
    /**
     * 更新用户对应用的可见性
     */
    void updateUserAppVisibility(Long userId, Long appId, Boolean visible);
}