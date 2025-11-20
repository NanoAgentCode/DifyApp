package com.github.app.dify.repository;

import com.github.app.dify.domain.UserAppVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户应用可见性Repository
 */
@Repository
public interface UserAppVisibilityRepository extends JpaRepository<UserAppVisibility, Long> {
    
    /**
     * 根据用户ID查找所有应用可见性
     */
    List<UserAppVisibility> findByUserId(Long userId);
    
    /**
     * 根据应用ID查找所有用户可见性
     */
    List<UserAppVisibility> findByAppId(Long appId);
    
    /**
     * 根据用户ID和应用ID查找可见性
     */
    Optional<UserAppVisibility> findByUserIdAndAppId(Long userId, Long appId);
    
    /**
     * 根据用户ID和可见性状态查找
     */
    List<UserAppVisibility> findByUserIdAndVisible(Long userId, Boolean visible);
    
    /**
     * 检查用户和应用的关系是否存在
     */
    boolean existsByUserIdAndAppId(Long userId, Long appId);
    
    /**
     * 删除用户的所有应用可见性
     */
    void deleteByUserId(Long userId);
    
    /**
     * 删除应用的所有用户可见性
     */
    void deleteByAppId(Long appId);
}

