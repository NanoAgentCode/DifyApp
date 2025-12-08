package com.github.app.dify.service;

import com.github.app.dify.resp.UserDataSourceVisibilityResp;

import java.util.List;

/**
 * 用户数据源可见性服务接口
 */
public interface UserDataSourceVisibilityService {
    
    /**
     * 获取用户的所有数据源可见性列表
     * 如果用户没有设置可见性，默认所有数据源都可见
     */
    List<UserDataSourceVisibilityResp> getUserDataSourceVisibilities(Long userId);
    
    /**
     * 更新用户对数据源的可见性
     */
    void updateUserDataSourceVisibility(Long userId, Long dataSourceId, Boolean visible);
    
    /**
     * 批量更新用户对数据源的可见性
     */
    void batchUpdateUserDataSourceVisibility(Long userId, List<Long> dataSourceIds, Boolean visible);
    
    /**
     * 检查用户是否有权限访问数据源
     */
    boolean hasAccess(Long userId, Long dataSourceId);
    
    /**
     * 检查用户是否被明确授予访问权限
     */
    boolean isExplicitlyGranted(Long userId, Long dataSourceId);
}
