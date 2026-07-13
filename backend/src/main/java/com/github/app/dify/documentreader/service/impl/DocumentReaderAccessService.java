package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.repository.DocumentReaderRepository;
import org.springframework.stereotype.Service;

@Service
class DocumentReaderAccessService {

    private final DocumentReaderRepository documentRepository;

    DocumentReaderAccessService(DocumentReaderRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    DocumentReader getDocumentAndValidateAccess(Long documentId, Long userId) {
        DocumentReader document = documentRepository.findByIdAndDeleted(documentId, 0)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        if (!document.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此文档", ErrorCode.FORBIDDEN);
        }
        return document;
    }

    void validateAccess(Long documentId, Long userId) {
        getDocumentAndValidateAccess(documentId, userId);
    }
}
