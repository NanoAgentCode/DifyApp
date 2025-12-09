package com.github.app.dify.repository;

import com.github.app.dify.domain.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 系统配置Repository
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    
    /**
     * 根据配置键查找配置（排除已删除的）
     */
    @Query("SELECT sc FROM SystemConfig sc WHERE (sc.deleted IS NULL OR sc.deleted = 0) " +
           "AND sc.configKey = :configKey")
    Optional<SystemConfig> findByConfigKeyAndNotDeleted(@Param("configKey") String configKey);
    
    /**
     * 根据配置分组查找配置列表（排除已删除的）
     */
    @Query("SELECT sc FROM SystemConfig sc WHERE (sc.deleted IS NULL OR sc.deleted = 0) " +
           "AND sc.configGroup = :configGroup")
    List<SystemConfig> findByConfigGroupAndNotDeleted(@Param("configGroup") String configGroup);
    
    /**
     * 查找所有未删除的配置
     */
    @Query("SELECT sc FROM SystemConfig sc WHERE (sc.deleted IS NULL OR sc.deleted = 0)")
    List<SystemConfig> findAllNotDeleted();
    
    /**
     * 根据配置键检查是否存在（排除已删除的）
     */
    @Query("SELECT COUNT(sc) > 0 FROM SystemConfig sc WHERE (sc.deleted IS NULL OR sc.deleted = 0) " +
           "AND sc.configKey = :configKey")
    boolean existsByConfigKeyAndNotDeleted(@Param("configKey") String configKey);
}

