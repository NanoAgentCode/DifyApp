package com.github.app.dify.repository;

import com.github.app.dify.domain.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库Repository
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    
    /**
     * 根据状态查找知识库列表
     */
    List<KnowledgeBase> findByStatus(Integer status);
    
    /**
     * 根据租户ID查找知识库列表
     */
    List<KnowledgeBase> findByTenantId(Integer tenantId);
    
    /**
     * 根据租户ID和状态查找知识库列表
     */
    List<KnowledgeBase> findByTenantIdAndStatus(Integer tenantId, Integer status);
    
    /**
     * 根据名称模糊查询知识库列表
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE (kb.deleted IS NULL OR kb.deleted = 0) " +
           "AND (kb.name LIKE %:keyword% OR kb.description LIKE %:keyword%)")
    List<KnowledgeBase> findByNameOrDescriptionContaining(@Param("keyword") String keyword);
    
    /**
     * 根据名称模糊查询和状态查找知识库列表
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE (kb.deleted IS NULL OR kb.deleted = 0) " +
           "AND kb.status = :status " +
           "AND (kb.name LIKE %:keyword% OR kb.description LIKE %:keyword%)")
    List<KnowledgeBase> findByStatusAndNameOrDescriptionContaining(
            @Param("status") Integer status, 
            @Param("keyword") String keyword);
    
    /**
     * 根据名称精确查找知识库（排除已删除的）
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE (kb.deleted IS NULL OR kb.deleted = 0) " +
           "AND kb.name = :name")
    List<KnowledgeBase> findByNameAndNotDeleted(@Param("name") String name);
    
    /**
     * 根据名称模糊查询和状态查找知识库列表（分页）
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE (kb.deleted IS NULL OR kb.deleted = 0) " +
           "AND (:status IS NULL OR kb.status = :status) " +
           "AND (:keyword IS NULL OR kb.name LIKE %:keyword% OR kb.description LIKE %:keyword%)")
    Page<KnowledgeBase> findByFiltersWithPagination(@Param("status") Integer status,
                                                     @Param("keyword") String keyword,
                                                     Pageable pageable);
}

