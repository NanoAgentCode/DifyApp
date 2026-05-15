package com.github.app.dify.system.repository;

import com.github.app.dify.system.domain.SystemConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
     * 按配置分组查询未删除的配置
     */
    List<SystemConfig> findByConfigGroupAndDeleted(String configGroup, Integer deleted);

    default List<SystemConfig> findByConfigGroupAndNotDeleted(String configGroup) {
        return findByConfigGroupAndDeleted(configGroup, 0);
    }

    /**
     * 按配置键查询（含已删除）
     */
    Optional<SystemConfig> findByConfigKey(String configKey);

    /**
     * 按删除状态查询，按分组、配置键排序（管理端列表用）
     */
    List<SystemConfig> findByDeletedOrderByConfigGroupAscConfigKeyAsc(Integer deleted);

    /**
     * 管理端分页查询配置，支持按分组和关键字筛选。
     */
    @Query("SELECT c FROM SystemConfig c " +
            "WHERE c.deleted = :deleted " +
            "AND (:configGroup IS NULL OR :configGroup = '' OR c.configGroup = :configGroup) " +
            "AND (:keyword IS NULL OR :keyword = '' " +
            "OR LOWER(c.configKey) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(c.configValue) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(c.configGroup) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<SystemConfig> searchConfigs(@Param("deleted") Integer deleted,
                                     @Param("configGroup") String configGroup,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);
}
