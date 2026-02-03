package com.github.app.dify.system.service;

import com.github.app.dify.system.domain.SystemConfig;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置服务（按 key 读取配置值，如 userlog.elasticsearchDataSourceId、observability.elasticsearchDataSourceId）
 */
public interface SystemConfigService {

    /**
     * 根据配置键获取配置值，未配置或已删除时返回 null
     */
    String getConfigValue(String configKey);

    /**
     * 根据配置分组获取未删除的配置列表（如 help、theme）
     */
    List<SystemConfig> getConfigsByGroup(String configGroup);

    /**
     * 获取所有未删除的配置（管理端列表）
     */
    List<SystemConfig> getAllConfigs();

    /**
     * 根据配置键获取完整配置实体（仅未删除）
     */
    Optional<SystemConfig> getConfigByKey(String configKey);

    /**
     * 新增或更新配置（按 configKey 唯一，存在则更新）
     */
    SystemConfig saveOrUpdate(SystemConfig config, Long creatorId, String creator);

    /**
     * 按配置键软删除
     */
    void deleteByKey(String configKey);
}
