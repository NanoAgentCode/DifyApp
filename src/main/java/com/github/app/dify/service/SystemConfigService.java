package com.github.app.dify.service;

import com.github.app.dify.req.UpdateSystemConfigReq;
import com.github.app.dify.resp.SystemConfigResp;
import java.util.List;

/**
 * 系统配置服务接口
 */
public interface SystemConfigService {
    
    /**
     * 根据配置键获取配置值
     * @param configKey 配置键
     * @return 配置值，如果不存在返回null
     */
    String getConfigValue(String configKey);
    
    /**
     * 根据配置键获取配置（完整信息）
     * @param configKey 配置键
     * @return 配置响应，如果不存在返回null
     */
    SystemConfigResp getConfigByKey(String configKey);
    
    /**
     * 根据配置分组获取配置列表
     * @param configGroup 配置分组
     * @return 配置列表
     */
    List<SystemConfigResp> getConfigsByGroup(String configGroup);
    
    /**
     * 获取所有配置
     * @return 配置列表
     */
    List<SystemConfigResp> getAllConfigs();
    
    /**
     * 设置或更新配置（如果存在则更新，不存在则创建）
     * @param req 更新请求
     * @param userId 用户ID
     * @param username 用户名
     * @return 配置响应
     */
    SystemConfigResp setOrUpdateConfig(UpdateSystemConfigReq req, Long userId, String username);
    
    /**
     * 删除配置（软删除）
     * @param configKey 配置键
     */
    void deleteConfig(String configKey);
}

