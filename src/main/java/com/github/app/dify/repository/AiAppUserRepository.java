package com.github.app.dify.repository;

import com.github.app.dify.domain.AiAppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * AI应用用户关联Repository
 */
@Repository
public interface AiAppUserRepository extends JpaRepository<AiAppUser, Long> {
    
    /**
     * 根据应用ID查找用户关联列表
     */
    List<AiAppUser> findByAppId(Long appId);
    
    /**
     * 根据用户ID查找应用关联列表
     */
    List<AiAppUser> findByUserId(String userId);
    
    /**
     * 根据应用ID和用户ID查找关联
     */
    Optional<AiAppUser> findByAppIdAndUserId(Long appId, String userId);
    
    /**
     * 根据应用ID和状态查找用户关联列表
     */
    List<AiAppUser> findByAppIdAndStatus(Long appId, Integer status);
    
    /**
     * 根据用户ID和状态查找应用关联列表
     */
    List<AiAppUser> findByUserIdAndStatus(String userId, Integer status);
    
    /**
     * 根据租户ID查找用户关联列表
     */
    List<AiAppUser> findByTenantId(Integer tenantId);
    
    /**
     * 检查用户是否已关联应用
     */
    boolean existsByAppIdAndUserId(Long appId, String userId);
}