package com.github.app.dify.permission.repository;

import com.github.app.dify.permission.domain.UserDataSourceVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * 用户数据源可见性Repository
 */
@Repository
public interface UserDataSourceVisibilityRepository extends JpaRepository<UserDataSourceVisibility, Long> {
    
    /**
     * 根据用户ID查找所有数据源可见性
     */
    List<UserDataSourceVisibility> findByUserId(Long userId);
    
    /**
     * 根据数据源ID查找所有用户可见性
     */
    List<UserDataSourceVisibility> findByDataSourceId(Long dataSourceId);
    
    /**
     * 根据用户ID和数据源ID查找可见性
     */
    Optional<UserDataSourceVisibility> findByUserIdAndDataSourceId(Long userId, Long dataSourceId);
    
    /**
     * 根据用户ID和可见性状态查找
     */
    List<UserDataSourceVisibility> findByUserIdAndVisible(Long userId, Boolean visible);
    
    /**
     * 检查用户和数据源的关系是否存在
     */
    boolean existsByUserIdAndDataSourceId(Long userId, Long dataSourceId);
    
    /**
     * 删除用户的所有数据源可见性
     */
    void deleteByUserId(Long userId);
    
    /**
     * 删除数据源的所有用户可见性
     */
    void deleteByDataSourceId(Long dataSourceId);
}

