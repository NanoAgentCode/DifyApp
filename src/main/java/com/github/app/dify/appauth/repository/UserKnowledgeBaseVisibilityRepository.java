package com.github.app.dify.appauth.repository;

import com.github.app.dify.appauth.domain.UserKnowledgeBaseVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * 用户知识库可见性Repository
 */
@Repository
public interface UserKnowledgeBaseVisibilityRepository extends JpaRepository<UserKnowledgeBaseVisibility, Long> {
    
    /**
     * 根据用户ID查找所有知识库可见性
     */
    List<UserKnowledgeBaseVisibility> findByUserId(Long userId);
    
    /**
     * 根据知识库ID查找所有用户可见性
     */
    List<UserKnowledgeBaseVisibility> findByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据用户ID和知识库ID查找可见性
     */
    Optional<UserKnowledgeBaseVisibility> findByUserIdAndKnowledgeBaseId(Long userId, Long knowledgeBaseId);
    
    /**
     * 根据用户ID和可见性状态查找
     */
    List<UserKnowledgeBaseVisibility> findByUserIdAndVisible(Long userId, Boolean visible);
    
    /**
     * 检查用户和知识库的关系是否存在
     */
    boolean existsByUserIdAndKnowledgeBaseId(Long userId, Long knowledgeBaseId);
    
    /**
     * 删除用户的所有知识库可见性
     */
    void deleteByUserId(Long userId);
    
    /**
     * 删除知识库的所有用户可见性
     */
    void deleteByKnowledgeBaseId(Long knowledgeBaseId);
}

