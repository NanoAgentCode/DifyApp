package com.github.app.dify.system.service;

/**
 * 系统配置服务（按 key 读取配置值，如 userlog.elasticsearchDataSourceId、observability.elasticsearchDataSourceId）
 */
public interface SystemConfigService {

    /**
     * 根据配置键获取配置值，未配置或已删除时返回 null
     */
    String getConfigValue(String configKey);
}
