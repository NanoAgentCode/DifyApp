package com.github.app.dify.knowledgebase.repository;

import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * 根据知识库ID和删除状态查找文档列表（分页）
     */
    Page<KnowledgeBaseDocument> findByKnowledgeBaseIdAndDeleted(Long knowledgeBaseId, Integer deleted, Pageable pageable);
    
    /**
     * 根据知识库ID、删除状态和文件名模糊查询（分页）
     */
    @Query("SELECT d FROM KnowledgeBaseDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND (d.deleted IS NULL OR d.deleted = :deleted) AND (:keyword IS NULL OR :keyword = '' OR d.originalFileName LIKE CONCAT('%', :keyword, '%'))")
    Page<KnowledgeBaseDocument> findByKnowledgeBaseIdAndDeletedAndKeyword(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("deleted") Integer deleted,
            @Param("keyword") String keyword,
            Pageable pageable);
    
    /**
     * 根据知识库ID、删除状态、向量化状态和文件名模糊查询（分页）
     */
    @Query("SELECT d FROM KnowledgeBaseDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND (d.deleted IS NULL OR d.deleted = :deleted) AND (:keyword IS NULL OR :keyword = '' OR d.originalFileName LIKE CONCAT('%', :keyword, '%')) AND (:vectorizedStatus IS NULL OR d.vectorizedStatus = :vectorizedStatus) AND (:fileType IS NULL OR :fileType = '' OR d.fileType = :fileType)")
    Page<KnowledgeBaseDocument> findByKnowledgeBaseIdAndDeletedAndFilters(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("deleted") Integer deleted,
            @Param("keyword") String keyword,
            @Param("vectorizedStatus") Integer vectorizedStatus,
            @Param("fileType") String fileType,
            Pageable pageable);
    
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