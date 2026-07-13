package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.documentreader.domain.*;
import com.github.app.dify.documentreader.repository.*;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import com.github.app.dify.documentreader.service.DocumentReaderService;
import com.github.app.dify.documentreader.service.DocumentReaderVectorizationService;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.system.config.DocumentReaderConfig;
import com.github.app.dify.system.service.SystemConfigService;
import com.github.app.dify.system.util.SkillLoader;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.github.app.dify.documentreader.util.DocumentReaderConverterUtil;
import com.github.app.dify.documentreader.util.DocumentReaderDateTimeUtil;
import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.common.util.PageUtil;
import com.github.app.dify.documentreader.util.DocumentReaderSoftDeleteUtil;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.app.dify.knowledgebase.service.DocumentParserService;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.time.Duration;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;

/**
 * 文档解读服务实现
 */
@Service
class DocumentReaderUploadDeleteService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderUploadDeleteService.class);
    private static final String[] ALLOWED_FILE_TYPES = {"pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "jpeg", "gif"};
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    @Autowired private DocumentReaderRepository documentRepository;
    @Autowired private DocumentGuideRepository guideRepository;
    @Autowired private DocumentTranslationRepository translationRepository;
    @Autowired private DocumentMindMapRepository mindMapRepository;
    @Autowired private DocumentNotesRepository notesRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private DocumentReaderVectorizationService documentReaderVectorizationService;
    @Autowired private DocumentReaderAccessService documentReaderAccessService;

    /**
     * 上传文档
     */
    @Transactional
    public DocumentReaderResp uploadDocument(MultipartFile file, Long userId) {
        logger.info("开始上传文档 - 文件名: {}, 用户ID: {}", file.getOriginalFilename(), userId);

        validateFile(file);
        String filePath = generateFilePath(userId, file.getOriginalFilename());
        String fileUrl = uploadFileToStorage(file, filePath);
        DocumentReader document = createDocumentEntity(file, userId, filePath, fileUrl);
        document = documentRepository.save(document);
        logger.info("文档上传成功 - 文档ID: {}, 文件名: {}", document.getId(), document.getOriginalFileName());

        triggerVectorizationAsync(document.getId(), file);

        return DocumentReaderConverterUtil.convertToResp(document);
    }

    /**
     * 上传文件到存储
     */
    private String uploadFileToStorage(MultipartFile file, String filePath) {
        try {
            return fileStorageService.uploadFile(file, filePath);
        } catch (Exception e) {
            logger.error("文件上传到MinIO失败: {}", filePath, e);
            throw new BusinessException("文件上传失败", ErrorCode.FILE_UPLOAD_FAILED, e);
        }
    }

    /**
     * 创建文档实体
     */
    private DocumentReader createDocumentEntity(MultipartFile file, Long userId, String filePath, String fileUrl) {
        DocumentReader document = new DocumentReader();
        document.setFileName(filePath.substring(filePath.lastIndexOf("/") + 1));
        document.setOriginalFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileUrl(fileUrl);
        document.setFileSize(file.getSize());
        document.setFileType(getFileExtension(file.getOriginalFilename()));
        document.setMimeType(file.getContentType());
        document.setStorageType("minio");
        document.setStatus(1);
        document.setUserId(userId);
        DocumentReaderDateTimeUtil.setCreateAndUpdateTime(document);
        document.setDeleted(0);
        document.setTotalPages(1);
        document.setVectorizedStatus(0);
        return document;
    }

    /**
     * 异步触发向量化
     */
    private void triggerVectorizationAsync(Long documentId, MultipartFile file) {
        try {
            documentReaderVectorizationService.vectorizeDocumentAsync(documentId, file);
            logger.info("已提交文档向量化任务 - 文档ID: {}", documentId);
        } catch (Exception e) {
            logger.error("提交文档向量化任务失败 - 文档ID: {}", documentId, e);
            // 不抛出异常，避免影响文档上传
        }
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long documentId, Long userId) {
        DocumentReader document = getDocumentByIdAndValidateAccess(documentId, userId);

        deleteFileFromStorage(document.getFilePath());
        deleteRelatedData(documentId);
        deleteDocumentVectors(documentId);
        softDeleteDocument(document);

        logger.info("文档删除成功 - 文档ID: {}", documentId);
    }

    /**
     * 获取文档并验证访问权限
     */
    private DocumentReader getDocumentByIdAndValidateAccess(Long documentId, Long userId) {
        return documentReaderAccessService.getDocumentAndValidateAccess(documentId, userId);
    }

    /**
     * 从存储删除文件
     */
    private void deleteFileFromStorage(String filePath) {
        try {
            fileStorageService.deleteFile(filePath);
        } catch (Exception e) {
            logger.error("从MinIO删除文件失败: {}", filePath, e);
        }
    }

    /**
     * 删除相关数据
     */
    private void deleteRelatedData(Long documentId) {
        guideRepository.findByDocumentId(documentId).ifPresent(guideRepository::delete);
        translationRepository.findByDocumentId(documentId).forEach(translationRepository::delete);
        mindMapRepository.findByDocumentId(documentId).ifPresent(mindMapRepository::delete);
        notesRepository.findByDocumentId(documentId).ifPresent(notesRepository::delete);
    }

    /**
     * 删除文档向量
     */
    private void deleteDocumentVectors(Long documentId) {
        try {
            documentReaderVectorizationService.deleteDocumentVectors(documentId);
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 文档ID: {}", documentId, e);
            // 不抛出异常，避免影响删除流程
        }
    }

    /**
     * 软删除文档
     */
    private void softDeleteDocument(DocumentReader document) {
        DocumentReaderSoftDeleteUtil.softDelete(document, documentRepository);
    }


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空", ErrorCode.FILE_UPLOAD_FAILED);
        }

        String fileExtension = getFileExtension(file.getOriginalFilename());
        if (fileExtension == null) {
            throw new BusinessException("无法识别文件类型", ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        boolean allowed = false;
        for (String allowedType : ALLOWED_FILE_TYPES) {
            if (allowedType.equalsIgnoreCase(fileExtension)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            throw new BusinessException("不支持的文件类型: " + fileExtension, ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过100MB", ErrorCode.FILE_TOO_LARGE);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    private String generateFilePath(Long userId, String originalFileName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        String datePath = sdf.format(DateTimeUtil.now());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExtension = getFileExtension(originalFileName);
        return String.format("document-reader/%d/%s/%s.%s", userId, datePath, uuid, fileExtension);
    }


}
