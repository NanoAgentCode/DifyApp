package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.repository.DocumentReaderRepository;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import com.github.app.dify.documentreader.util.DocumentReaderConverterUtil;
import com.github.app.dify.common.util.PageUtil;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
class DocumentReaderReadService {
    private final DocumentReaderRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentReaderAccessService accessService;
    private final DocumentReaderGuideMindMapService guideMindMapService;

    DocumentReaderReadService(DocumentReaderRepository documentRepository, FileStorageService fileStorageService,
                              DocumentReaderAccessService accessService, DocumentReaderGuideMindMapService guideMindMapService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.accessService = accessService;
        this.guideMindMapService = guideMindMapService;
    }

    DocumentReaderResp getDocumentById(Long documentId, Long userId) {
        return DocumentReaderConverterUtil.convertToResp(accessService.getDocumentAndValidateAccess(documentId, userId));
    }

    PageResponse<DocumentReaderResp> listDocuments(Long userId, String keyword, String fileType, int page, int pageSize) {
        Pageable pageable = PageUtil.createPageable(page, pageSize);
        Page<DocumentReader> documents = documentRepository.findByUserIdAndDeletedAndKeywordAndFileType(
                userId, 0, keyword == null ? null : keyword.trim(), fileType == null || fileType.isEmpty() ? null : fileType, pageable);
        return PageUtil.toPageResponse(documents, DocumentReaderConverterUtil::convertToResp);
    }

    InputStream getDocumentContent(Long documentId, Long userId, Integer page) {
        DocumentReader document = accessService.getDocumentAndValidateAccess(documentId, userId);
        try {
            return fileStorageService.downloadFile(document.getFilePath());
        } catch (Exception e) {
            throw new BusinessException("获取文档内容失败", ErrorCode.SYSTEM_BUSY, e);
        }
    }

    String getDocumentText(Long documentId, Long userId) {
        return guideMindMapService.extractDocumentText(accessService.getDocumentAndValidateAccess(documentId, userId));
    }
}
