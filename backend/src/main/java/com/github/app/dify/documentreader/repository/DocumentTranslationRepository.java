package com.github.app.dify.documentreader.repository;

import com.github.app.dify.documentreader.domain.DocumentTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 文档翻译Repository
 */
@Repository
public interface DocumentTranslationRepository extends JpaRepository<DocumentTranslation, Long> {
    
    /**
     * 根据文档ID和目标语言查找翻译
     */
    Optional<DocumentTranslation> findByDocumentIdAndTargetLanguage(Long documentId, String targetLanguage);
    
    /**
     * 根据文档ID查找所有翻译
     */
    List<DocumentTranslation> findByDocumentId(Long documentId);
}

