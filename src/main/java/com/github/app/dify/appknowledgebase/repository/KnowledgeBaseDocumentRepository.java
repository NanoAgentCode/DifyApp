package com.github.app.dify.appknowledgebase.repository;

import com.github.app.dify.appknowledgebase.domain.KnowledgeBaseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
/**
 * 知识库文档Repository
 */
@Repository
public interface KnowledgeBaseDocumentRepository extends JpaRepository<KnowledgeBaseDocument, Long> {
    
    /**
     * 根据知识库ID查找文档列表
     */
    List<KnowledgeBaseDocument> findByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据知识库ID和删除状态查找文档列表
     */
    List<KnowledgeBaseDocument> findByKnowledgeBaseIdAndDeleted(Long knowledgeBaseId, Integer deleted);
    
    /**
     * 统计知识库的文档数量（未删除）
     */
    @Query("SELECT COUNT(d) FROM KnowledgeBaseDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND (d.deleted IS NULL OR d.deleted = 0)")
    Long countByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    /**
     * 统计知识库中成功向量化的文档数量（未删除，向量化状态为2）
     */
    @Query("SELECT COUNT(d) FROM KnowledgeBaseDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND (d.deleted IS NULL OR d.deleted = 0) AND d.vectorizedStatus = 2")
    Long countSuccessDocumentsByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    /**
     * 统计知识库中向量化失败的文档数量（未删除，向量化状态为3）
     */
    @Query("SELECT COUNT(d) FROM KnowledgeBaseDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND (d.deleted IS NULL OR d.deleted = 0) AND d.vectorizedStatus = 3")
    Long countFailedDocumentsByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
}