package com.github.app.dify.documentreader.repository;

import com.github.app.dify.documentreader.domain.DocumentMindMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 文档脑图Repository
 */
@Repository
public interface DocumentMindMapRepository extends JpaRepository<DocumentMindMap, Long> {
    
    /**
     * 根据文档ID查找脑图
     */
    Optional<DocumentMindMap> findByDocumentId(Long documentId);
}

