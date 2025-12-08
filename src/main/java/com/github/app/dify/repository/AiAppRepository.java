package com.github.app.dify.repository;

import com.github.app.dify.domain.AiApp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * 根据关键词搜索应用（名称或描述）
     */
    @Query("SELECT a FROM AiApp a WHERE (a.deleted IS NULL OR a.deleted = 0) " +
           "AND (LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<AiApp> searchByKeyword(@Param("keyword") String keyword);
    
    /**
     * 根据关键词、类型和状态搜索应用
     */
    @Query("SELECT a FROM AiApp a WHERE (a.deleted IS NULL OR a.deleted = 0) " +
           "AND (LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:type IS NULL OR a.type = :type) " +
           "AND (:status IS NULL OR a.status = :status)")
    List<AiApp> searchByKeywordAndFilters(@Param("keyword") String keyword, 
                                          @Param("type") Integer type, 
                                          @Param("status") Integer status);
    
    /**
     * 根据关键词、类型和状态搜索应用（分页）
     */
    @Query("SELECT a FROM AiApp a WHERE (a.deleted IS NULL OR a.deleted = 0) " +
           "AND (LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:type IS NULL OR a.type = :type) " +
           "AND (:status IS NULL OR a.status = :status)")
    Page<AiApp> searchByKeywordAndFiltersWithPagination(@Param("keyword") String keyword, 
                                                          @Param("type") Integer type, 
                                                          @Param("status") Integer status,
                                                          Pageable pageable);
    
    /**
     * 根据类型和状态查找应用列表（分页）
     */
    @Query("SELECT a FROM AiApp a WHERE (a.deleted IS NULL OR a.deleted = 0) " +
           "AND (:type IS NULL OR a.type = :type) " +
           "AND (:status IS NULL OR a.status = :status)")
    Page<AiApp> findByFiltersWithPagination(@Param("type") Integer type, 
                                             @Param("status") Integer status,
                                             Pageable pageable);
    
    /**
     * 检查API Key是否存在
     */
    boolean existsByAppId(String appId);
}