package com.github.app.dify.system.repository;

import com.github.app.dify.system.domain.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 系统配置表 Repository
 */
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /**
     * 按配置键查询未删除的配置
     */
    Optional<SystemConfig> findByConfigKeyAndDeleted(String configKey, Integer deleted);

    default Optional<SystemConfig> findByConfigKeyAndNotDeleted(String configKey) {
        return findByConfigKeyAndDeleted(configKey, 0);
    }

    /**
     * 按配置键查询（含已删除）
     */
    Optional<SystemConfig> findByConfigKey(String configKey);
}
