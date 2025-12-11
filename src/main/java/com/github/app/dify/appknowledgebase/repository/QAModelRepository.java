package com.github.app.dify.appknowledgebase.repository;

import com.github.app.dify.appknowledgebase.domain.QAModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * 问答模型Repository
 */
@Repository
public interface QAModelRepository extends JpaRepository<QAModel, Long> {
    
    /**
     * 查找所有未删除的模型
     */
    @Query("SELECT q FROM QAModel q WHERE (q.deleted IS NULL OR q.deleted = 0) ORDER BY q.createTime DESC")
    List<QAModel> findAllActive();
    
    /**
     * 根据使用场景查找模型
     */
    @Query("SELECT q FROM QAModel q WHERE (q.deleted IS NULL OR q.deleted = 0) " +
           "AND (q.useFor = :useFor OR q.useFor = 'both') " +
           "AND q.enabled = true " +
           "ORDER BY q.isDefault DESC, q.createTime DESC")
    List<QAModel> findByUseFor(@Param("useFor") String useFor);
    
    /**
     * 根据使用场景查找默认模型
     */
    @Query("SELECT q FROM QAModel q WHERE (q.deleted IS NULL OR q.deleted = 0) " +
           "AND (q.useFor = :useFor OR q.useFor = 'both') " +
           "AND q.isDefault = true " +
           "AND q.enabled = true")
    Optional<QAModel> findDefaultByUseFor(@Param("useFor") String useFor);
    
    /**
     * 查找所有启用的模型
     */
    @Query("SELECT q FROM QAModel q WHERE (q.deleted IS NULL OR q.deleted = 0) " +
           "AND q.enabled = true " +
           "ORDER BY q.isDefault DESC, q.createTime DESC")
    List<QAModel> findAllEnabled();
}