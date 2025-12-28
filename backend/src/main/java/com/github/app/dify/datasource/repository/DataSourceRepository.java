package com.github.app.dify.datasource.repository;

import com.github.app.dify.datasource.domain.DataSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
/**
 * 数据源Repository
 */
@Repository
public interface DataSourceRepository extends JpaRepository<DataSource, Long> {
    
    /**
     * 根据状态查找数据源列表
     */
    List<DataSource> findByStatus(Integer status);
    
    /**
     * 根据租户ID查找数据源列表
     */
    List<DataSource> findByTenantId(Integer tenantId);
    
    /**
     * 根据租户ID和状态查找数据源列表
     */
    List<DataSource> findByTenantIdAndStatus(Integer tenantId, Integer status);
    
    /**
     * 根据名称模糊查询数据源列表
     */
    @Query("SELECT ds FROM DataSource ds WHERE (ds.deleted IS NULL OR ds.deleted = 0) " +
           "AND (ds.name LIKE %:keyword% OR ds.description LIKE %:keyword%)")
    List<DataSource> findByNameOrDescriptionContaining(@Param("keyword") String keyword);
    
    /**
     * 根据名称模糊查询和状态查找数据源列表
     */
    @Query("SELECT ds FROM DataSource ds WHERE (ds.deleted IS NULL OR ds.deleted = 0) " +
           "AND ds.status = :status " +
           "AND (ds.name LIKE %:keyword% OR ds.description LIKE %:keyword%)")
    List<DataSource> findByStatusAndNameOrDescriptionContaining(
            @Param("status") Integer status, 
            @Param("keyword") String keyword);
    
    /**
     * 根据名称精确查找数据源（排除已删除的）
     */
    @Query("SELECT ds FROM DataSource ds WHERE (ds.deleted IS NULL OR ds.deleted = 0) " +
           "AND ds.name = :name")
    List<DataSource> findByNameAndNotDeleted(@Param("name") String name);
    
    /**
     * 根据名称模糊查询和状态查找数据源列表（分页）
     */
    @Query("SELECT ds FROM DataSource ds WHERE (ds.deleted IS NULL OR ds.deleted = 0) " +
           "AND (:status IS NULL OR ds.status = :status) " +
           "AND (:keyword IS NULL OR ds.name LIKE %:keyword% OR ds.description LIKE %:keyword%) " +
           "AND (:type IS NULL OR ds.type = :type)")
    Page<DataSource> findByFiltersWithPagination(@Param("status") Integer status,
                                                 @Param("keyword") String keyword,
                                                 @Param("type") String type,
                                                 Pageable pageable);
}

