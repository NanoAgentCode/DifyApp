package com.github.app.dify.documentreader.repository;

import com.github.app.dify.documentreader.domain.DocumentGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 文档导读Repository
 */
@Repository
public interface DocumentGuideRepository extends JpaRepository<DocumentGuide, Long> {
    
    /**
     * 根据文档ID查找导读
     */
    Optional<DocumentGuide> findByDocumentId(Long documentId);
}

