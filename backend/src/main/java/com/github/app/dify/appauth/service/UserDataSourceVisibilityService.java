package com.github.app.dify.appauth.service;

import com.github.app.dify.appauth.resp.UserDataSourceVisibilityResp;
import java.util.List;

/**
 * 用户数据源可见性服务接口
 */
public interface UserDataSourceVisibilityService {
    
    /**
     * 检查用户是否有权限访问数据源
     * 
     * @param userId 用户ID
     * @param dataSourceId 数据源ID
     * @return true-有权限，false-无权限
     */
    boolean hasAccess(Long userId, Long dataSourceId);
    
    /**
     * 检查用户是否被明确授权访问数据源
     * （在UserDataSourceVisibility表中有记录且visible=true）
     * 
     * @param userId 用户ID
     * @param dataSourceId 数据源ID
     * @return true-被明确授权，false-未被明确授权
     */
    boolean isExplicitlyGranted(Long userId, Long dataSourceId);
    
    /**
     * 获取用户的数据源可见性列表
     * 
     * @param userId 用户ID
     * @return 数据源可见性列表
     */
    List<UserDataSourceVisibilityResp> getUserDataSourceVisibilities(Long userId);
    
    /**
     * 更新用户对数据源的可见性
     * 
     * @param userId 用户ID
     * @param dataSourceId 数据源ID
     * @param visible 是否可见
     */
    void updateUserDataSourceVisibility(Long userId, Long dataSourceId, Boolean visible);
    
    /**
     * 批量更新用户对数据源的可见性
     * 
     * @param userId 用户ID
     * @param dataSourceIds 数据源ID列表
     * @param visible 是否可见
     */
    void batchUpdateUserDataSourceVisibility(Long userId, List<Long> dataSourceIds, Boolean visible);
}

