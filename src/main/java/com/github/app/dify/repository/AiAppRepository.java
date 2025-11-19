package com.github.app.dify.repository;

import com.github.app.dify.domain.AiApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI应用Repository
 */
@Repository
public interface AiAppRepository extends JpaRepository<AiApp, Long> {
    
    /**
     * 根据Dify API Key查找应用
     */
    Optional<AiApp> findByAppId(String appId);
    
    /**
     * 根据应用类型查找应用列表
     */
    List<AiApp> findByType(Integer type);
    
    /**
     * 根据状态查找应用列表
     */
    List<AiApp> findByStatus(Integer status);
    
    /**
     * 根据租户ID查找应用列表
     */
    List<AiApp> findByTenantId(Integer tenantId);
    
    /**
     * 根据租户ID和状态查找应用列表
     */
    List<AiApp> findByTenantIdAndStatus(Integer tenantId, Integer status);
    
    /**
     * 根据租户ID和类型查找应用列表
     */
    List<AiApp> findByTenantIdAndType(Integer tenantId, Integer type);
    
    /**
     * 根据租户ID、类型和状态查找应用列表
     */
    List<AiApp> findByTenantIdAndTypeAndStatus(Integer tenantId, Integer type, Integer status);
    
    /**
     * 检查API Key是否存在
     */
    boolean existsByAppId(String appId);
}

