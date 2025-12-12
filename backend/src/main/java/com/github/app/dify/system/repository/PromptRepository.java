package com.github.app.dify.system.repository;

import com.github.app.dify.system.domain.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 提示词Repository
 */
@Repository
public interface PromptRepository extends JpaRepository<Prompt, Long> {
    
    /**
     * 查找所有未删除的提示词
     */
    @Query("SELECT p FROM Prompt p WHERE (p.deleted IS NULL OR p.deleted = 0) ORDER BY p.createTime DESC")
    List<Prompt> findAllNotDeleted();
    
    /**
     * 根据标题模糊查询提示词列表
     */
    @Query("SELECT p FROM Prompt p WHERE (p.deleted IS NULL OR p.deleted = 0) " +
           "AND (p.title LIKE %:keyword% OR p.content LIKE %:keyword%) " +
           "ORDER BY p.createTime DESC")
    List<Prompt> findByTitleOrContentContaining(@Param("keyword") String keyword);
    
    /**
     * 根据标题精确查找提示词（排除已删除的）
     */
    @Query("SELECT p FROM Prompt p WHERE (p.deleted IS NULL OR p.deleted = 0) " +
           "AND p.title = :title")
    List<Prompt> findByTitleAndNotDeleted(@Param("title") String title);
}
