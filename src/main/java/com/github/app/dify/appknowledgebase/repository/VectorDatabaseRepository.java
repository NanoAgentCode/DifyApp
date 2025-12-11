package com.github.app.dify.appknowledgebase.repository;

import com.github.app.dify.appknowledgebase.domain.VectorDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * 向量数据库配置Repository
 */
@Repository
public interface VectorDatabaseRepository extends JpaRepository<VectorDatabase, Long> {
    
    /**
     * 查找所有未删除的配置
     */
    @Query("SELECT v FROM VectorDatabase v WHERE (v.deleted IS NULL OR v.deleted = 0) ORDER BY v.createTime DESC")
    List<VectorDatabase> findAllActive();
    
    /**
     * 根据类型查找所有未删除的配置
     */
    @Query("SELECT v FROM VectorDatabase v WHERE (v.deleted IS NULL OR v.deleted = 0) " +
           "AND v.type = ?1 ORDER BY v.isDefault DESC, v.createTime DESC")
    List<VectorDatabase> findByType(String type);
    
    /**
     * 查找默认的启用配置（按类型）
     */
    @Query("SELECT v FROM VectorDatabase v WHERE (v.deleted IS NULL OR v.deleted = 0) " +
           "AND v.type = ?1 " +
           "AND v.isDefault = true " +
           "AND v.enabled = true")
    Optional<VectorDatabase> findDefaultEnabledByType(String type);
    
    /**
     * 查找所有启用的配置（按类型）
     */
    @Query("SELECT v FROM VectorDatabase v WHERE (v.deleted IS NULL OR v.deleted = 0) " +
           "AND v.type = ?1 " +
           "AND v.enabled = true " +
           "ORDER BY v.isDefault DESC, v.createTime DESC")
    List<VectorDatabase> findAllEnabledByType(String type);
    
    /**
     * 查找所有启用的配置
     */
    @Query("SELECT v FROM VectorDatabase v WHERE (v.deleted IS NULL OR v.deleted = 0) " +
           "AND v.enabled = true " +
           "ORDER BY v.type, v.isDefault DESC, v.createTime DESC")
    List<VectorDatabase> findAllEnabled();
}