package com.github.app.dify.documentreader.repository;

import com.github.app.dify.documentreader.domain.DocumentNotes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 文档笔记Repository
 */
@Repository
public interface DocumentNotesRepository extends JpaRepository<DocumentNotes, Long> {
    
    /**
     * 根据文档ID查找笔记
     */
    Optional<DocumentNotes> findByDocumentId(Long documentId);
}

