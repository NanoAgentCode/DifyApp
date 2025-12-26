package com.github.app.dify.documentreader.repository;

import com.github.app.dify.documentreader.domain.DocumentReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 文档解读Repository
 */
@Repository
public interface DocumentReaderRepository extends JpaRepository<DocumentReader, Long> {
    
    /**
     * 根据ID和删除状态查找文档
     */
    Optional<DocumentReader> findByIdAndDeleted(Long id, Integer deleted);
    
    /**
     * 根据用户ID查找文档列表
     */
    List<DocumentReader> findByUserIdAndDeletedOrderByCreateTimeDesc(Long userId, Integer deleted);
    
    /**
     * 根据用户ID和删除状态分页查找文档
     */
    Page<DocumentReader> findByUserIdAndDeletedOrderByCreateTimeDesc(Long userId, Integer deleted, Pageable pageable);
    
    /**
     * 根据用户ID、文件名关键词和删除状态分页查找文档
     */
    @Query("SELECT d FROM DocumentReader d WHERE d.userId = :userId AND d.deleted = :deleted " +
           "AND (:keyword IS NULL OR :keyword = '' OR d.originalFileName LIKE %:keyword%) " +
           "AND (:fileType IS NULL OR :fileType = '' OR d.fileType = :fileType) " +
           "ORDER BY d.createTime DESC")
    Page<DocumentReader> findByUserIdAndDeletedAndKeywordAndFileType(
            @Param("userId") Long userId,
            @Param("deleted") Integer deleted,
            @Param("keyword") String keyword,
            @Param("fileType") String fileType,
            Pageable pageable);
}

