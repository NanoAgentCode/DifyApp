package com.github.app.dify.repository;

import com.github.app.dify.domain.EmbeddingModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * 向量化模型Repository
 */
@Repository
public interface EmbeddingModelRepository extends JpaRepository<EmbeddingModel, Long> {
    
    /**
     * 查找所有未删除的模型
     */
    @Query("SELECT e FROM EmbeddingModel e WHERE (e.deleted IS NULL OR e.deleted = 0) ORDER BY e.createTime DESC")
    List<EmbeddingModel> findAllActive();
    
    /**
     * 查找默认的启用模型
     */
    @Query("SELECT e FROM EmbeddingModel e WHERE (e.deleted IS NULL OR e.deleted = 0) " +
           "AND e.isDefault = true " +
           "AND e.enabled = true")
    Optional<EmbeddingModel> findDefaultEnabled();
    
    /**
     * 查找所有启用的模型
     */
    @Query("SELECT e FROM EmbeddingModel e WHERE (e.deleted IS NULL OR e.deleted = 0) " +
           "AND e.enabled = true " +
           "ORDER BY e.isDefault DESC, e.createTime DESC")
    List<EmbeddingModel> findAllEnabled();
}